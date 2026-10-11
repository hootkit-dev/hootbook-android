#!/usr/bin/env python3
"""Check CI signing credentials without Android SDK, Gradle, or secret argv."""

from __future__ import annotations

import argparse
import base64
import binascii
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile


REQUIRED = ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
JAVA_FAILURES = {
    10: "Cannot open keystore: check KEYSTORE_BASE64 and KEYSTORE_PASSWORD (JKS/PKCS12 required).",
    11: "KEY_ALIAS does not identify a private-key entry in this keystore.",
    12: "Cannot decrypt the private key with KEY_PASSWORD.",
    13: "Signing key algorithm is unsupported; Android signing requires RSA, EC, or DSA.",
    14: "Private-key entry has no signing certificate.",
    15: "Private key could not sign the temporary verification payload.",
    16: "Temporary payload signature did not verify with the signing certificate.",
    17: "Signing certificate does not match the established release SHA-256 fingerprint.",
    18: "RELEASE_CERT_SHA256 must be a 64-character hexadecimal fingerprint.",
}


class PreflightError(Exception):
    """A fixed, safe-to-log diagnosis; never wrap raw provider exceptions."""


def decode_keystore(encoded: str) -> bytes:
    # Permit normal base64 line wrapping, but not arbitrary ignored characters,
    # missing/extra padding, noncanonical pad bits, or a data: URL prefix.
    compact = encoded.replace("\r", "").replace("\n", "")
    try:
        decoded = base64.b64decode(compact, validate=True)
        canonical = base64.b64encode(decoded).decode("ascii")
    except (binascii.Error, ValueError):
        raise PreflightError("KEYSTORE_BASE64 is not valid canonical Base64.") from None
    if not decoded:
        raise PreflightError("Decoded keystore is empty.")
    if canonical != compact:
        raise PreflightError("KEYSTORE_BASE64 is not valid canonical Base64.")
    return decoded


def validate_keystore(staged: Path, probe_dir: Path, environment: dict[str, str]) -> None:
    java = shutil.which("java")
    if java is None:
        raise PreflightError("Signing preflight requires JDK 21 after actions/setup-java.")
    child_env = dict(environment)
    child_env.pop("KEYSTORE_BASE64", None)
    # Java receives only file paths as arguments; all credentials stay in env.
    command = [java, str(Path(__file__).with_name("SigningPreflight.java")), str(staged), str(probe_dir)]
    try:
        result = subprocess.run(
            command, env=child_env, stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            timeout=60, check=False,
        )
    except subprocess.TimeoutExpired:
        raise PreflightError("Signing preflight timed out; no build was started.") from None
    except OSError:
        raise PreflightError("Could not launch JDK signing preflight.") from None
    if result.returncode:
        raise PreflightError(JAVA_FAILURES.get(
            result.returncode,
            "JDK signing preflight failed; check that a complete JDK 21 is installed.",
        ))


def preflight(destination: Path, environment: dict[str, str]) -> None:
    missing = [name for name in REQUIRED if not environment.get(name)]
    if missing:
        raise PreflightError("Missing required signing secrets: " + ", ".join(missing) + ".")
    decoded = decode_keystore(environment["KEYSTORE_BASE64"])
    destination = destination.absolute()
    # A second invocation must never replace an existing file or follow a symlink.
    if destination.exists() or destination.is_symlink():
        raise PreflightError("Signing output already exists; remove it with the job cleanup step before retrying.")
    if any(character in str(destination) for character in ("\r", "\n")):
        raise PreflightError("Signing output path must not contain line breaks.")
    created_output = False
    completed = False
    previous_umask = os.umask(0o077)
    try:
        # All intermediate keystores, test payloads and signatures are scoped to
        # this private directory and removed even if validation/output fails.
        with tempfile.TemporaryDirectory(
            prefix="hootbook-signing-preflight-", dir=environment.get("RUNNER_TEMP") or None,
        ) as directory:
            probe_dir = Path(directory)
            staged = probe_dir / "release.keystore"
            staged.write_bytes(decoded)
            validate_keystore(staged, probe_dir, environment)
            # O_EXCL prevents a TOCTOU replacement of a file/symlink. Creation
            # with mode 0600 means the output is never briefly world-readable.
            descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            created_output = True
            with os.fdopen(descriptor, "wb") as output:
                output.write(decoded)
            github_output = environment.get("GITHUB_OUTPUT")
            if github_output:
                with open(github_output, "a", encoding="utf-8") as output:
                    output.write(f"keystore-path={destination}\n")
        completed = True
    except OSError:
        raise PreflightError("Could not securely create signing files or publish the validated keystore path.") from None
    finally:
        os.umask(previous_umask)
        if created_output and not completed:
            destination.unlink(missing_ok=True)


def interrupted(_signum: int, _frame: object) -> None:
    # Raise through context managers so runner cancellation cleans probe files.
    raise KeyboardInterrupt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keystore-out", required=True, type=Path)
    arguments = parser.parse_args()
    signal.signal(signal.SIGTERM, interrupted)
    try:
        preflight(arguments.keystore_out, dict(os.environ))
    except PreflightError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("::error::Signing preflight was cancelled.", file=sys.stderr)
        return 130
    print("Signing preflight passed: keystore opened, private key unlocked, test signature verified.")
    print("This checks signing-key readiness; built APKs still require apksigner verification.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
