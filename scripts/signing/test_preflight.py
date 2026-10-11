"""Offline regression tests. All keystores are throwaway fixtures in TemporaryDirectory."""

import base64
from contextlib import contextmanager
import importlib.util
import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("preflight.py")
spec = importlib.util.spec_from_file_location("signing_preflight", SCRIPT)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class RepositoryPinTests(unittest.TestCase):
    def test_workflows_pin_owner_confirmed_hootbook_certificate(self):
        root = Path(__file__).resolve().parents[2]
        expected = "f3bd2660565ef8dc23e61ef5c7b48b34e1cb21244b97847d5da008ae8a9ea3a5"
        for relative in (
            ".github/actions/signing-preflight/action.yml",
            ".github/workflows/debug-apk.yml",
            ".github/workflows/testing-apk.yml",
            ".github/workflows/release.yml",
        ):
            with self.subTest(path=relative):
                pins = re.findall(
                    r"^[ \t]*RELEASE_CERT_SHA256:[ \t]*(\S+)[ \t]*$",
                    (root / relative).read_text(),
                    re.MULTILINE,
                )
                self.assertEqual(pins, [expected])


class Base64Tests(unittest.TestCase):
    def test_strict_decode_allows_only_line_wrapping(self):
        self.assertEqual(preflight.decode_keystore("a2V5\r\nc3RvcmU=\n"), b"keystore")
        for value in ("@@@", "a2 V5", "a2V5\t", "a2V5é", "Zg", "Zg===", "Zh==", "data:;base64,Zg=="):
            with self.subTest(value=value), self.assertRaises(preflight.PreflightError):
                preflight.decode_keystore(value)

    def test_empty_decode_fails(self):
        for value in ("", "\r\n"):
            with self.subTest(value=value), self.assertRaisesRegex(preflight.PreflightError, "empty"):
                preflight.decode_keystore(value)


@unittest.skipUnless(shutil.which("java") and shutil.which("keytool"), "JDK 21 and keytool required")
class SigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixtures = tempfile.TemporaryDirectory(prefix="hootbook-dummy-signing-tests-")
        cls.addClassCleanup(cls.fixtures.cleanup)
        cls.root = Path(cls.fixtures.name)
        cls.store_password = "dummy store$'\" password"
        cls.key_password = "dummy key$'\" password"
        cls.alias = "dummy-signing-alias"
        cls.env = dict(os.environ)
        # Never consume inherited real signing credentials, even in developer CI.
        for name in preflight.REQUIRED:
            cls.env.pop(name, None)
        cls.env.pop("RELEASE_CERT_SHA256", None)
        cls.env.update(
            KEYSTORE_PASSWORD=cls.store_password,
            KEY_PASSWORD=cls.key_password,
            KEY_ALIAS=cls.alias,
        )
        cls.jks = cls.root / "dummy.jks"
        cls.keytool(
            "-genkeypair", "-alias", cls.alias, "-keyalg", "RSA", "-keysize", "2048",
            "-dname", "CN=Ephemeral Hootbook Unit Test", "-validity", "2",
            "-keystore", str(cls.jks), "-storetype", "JKS",
            "-storepass:env", "KEYSTORE_PASSWORD", "-keypass:env", "KEY_PASSWORD",
        )
        cert = cls.root / "dummy.cer"
        cls.keytool(
            "-exportcert", "-alias", cls.alias, "-keystore", str(cls.jks),
            "-storepass:env", "KEYSTORE_PASSWORD", "-file", str(cert),
        )
        cls.keytool(
            "-importcert", "-noprompt", "-alias", "certificate-only", "-file", str(cert),
            "-keystore", str(cls.jks), "-storepass:env", "KEYSTORE_PASSWORD",
        )
        cls.fingerprint = hashlib.sha256(cert.read_bytes()).hexdigest()
        # Preserve distinct store/key passwords in PKCS12 using the Java API;
        # keytool -genkeypair otherwise silently reuses the store password.
        convert = cls.root / "FixtureConversion.java"
        convert.write_text('''
import java.nio.file.*;
import java.security.*;
class FixtureConversion {
  public static void main(String[] args) throws Exception {
    char[] store = System.getenv("KEYSTORE_PASSWORD").toCharArray();
    char[] key = System.getenv("KEY_PASSWORD").toCharArray();
    String alias = System.getenv("KEY_ALIAS");
    KeyStore source = KeyStore.getInstance(Path.of(args[0]).toFile(), store);
    KeyStore target = KeyStore.getInstance("PKCS12");
    target.load(null, store);
    target.setKeyEntry(alias, source.getKey(alias, key), key, source.getCertificateChain(alias));
    try (var out = Files.newOutputStream(Path.of(args[1]))) { target.store(out, store); }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    source.setKeyEntry(alias, generator.generateKeyPair().getPrivate(), key,
        source.getCertificateChain(alias));
    try (var out = Files.newOutputStream(Path.of(args[2]))) { source.store(out, store); }
  }
}
''')
        cls.pkcs12 = cls.root / "dummy.p12"
        cls.mismatch = cls.root / "mismatched-certificate.jks"
        converted = subprocess.run(
            [shutil.which("java"), str(convert), str(cls.jks), str(cls.pkcs12), str(cls.mismatch)],
            env=cls.env, capture_output=True, text=True, timeout=30,
        )
        if converted.returncode:
            raise AssertionError("JDK 21 fixture conversion failed; no real credentials were used")

    @classmethod
    def keytool(cls, *arguments):
        result = subprocess.run(
            [shutil.which("keytool"), *arguments], env=cls.env, stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30,
        )
        if result.returncode:
            raise AssertionError("Could not create ephemeral signing test fixture")

    @contextmanager
    def invocation(self, keystore=None):
        with tempfile.TemporaryDirectory(prefix="hootbook-preflight-case-") as directory:
            root = Path(directory)
            environment = dict(self.env)
            environment.update(
                KEYSTORE_BASE64=base64.b64encode((keystore or self.jks).read_bytes()).decode(),
                RUNNER_TEMP=str(root),
                GITHUB_OUTPUT=str(root / "github-output"),
            )
            yield root, root / "validated.keystore", environment

    def run_script(self, output, environment):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--keystore-out", str(output)],
            env=environment, capture_output=True, text=True, timeout=70,
        )
        transcript = result.stdout + result.stderr
        for name in preflight.REQUIRED:
            value = environment.get(name)
            if value:
                self.assertNotIn(value, transcript, name + " was exposed")
        return result

    def assert_clean_failure(self, root, output, environment, diagnostic):
        result = self.run_script(output, environment)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(diagnostic, result.stderr)
        self.assertFalse(output.exists())
        self.assertFalse(list(root.glob("hootbook-signing-preflight-*")))
        self.assertFalse(Path(environment["GITHUB_OUTPUT"]).exists())

    def test_success_jks_with_separate_key_password_and_self_signed_certificate(self):
        with self.invocation() as (root, output, environment):
            result = self.run_script(output, environment)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("test signature verified", result.stdout)
            self.assertEqual(output.read_bytes(), self.jks.read_bytes())
            self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
            self.assertEqual(Path(environment["GITHUB_OUTPUT"]).read_text(), f"keystore-path={output}\n")
            self.assertFalse(list(root.glob("hootbook-signing-preflight-*")))

    def test_success_pkcs12_with_separate_key_password(self):
        with self.invocation(self.pkcs12) as (root, output, environment):
            result = self.run_script(output, environment)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(output.read_bytes(), self.pkcs12.read_bytes())
            self.assertFalse(list(root.glob("hootbook-signing-preflight-*")))

    def test_expected_certificate_fingerprint(self):
        with self.invocation() as (root, output, environment):
            environment["RELEASE_CERT_SHA256"] = self.fingerprint.upper()
            result = self.run_script(output, environment)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_unexpected_certificate_fingerprint(self):
        with self.invocation() as (root, output, environment):
            environment["RELEASE_CERT_SHA256"] = "0" * 64
            self.assert_clean_failure(root, output, environment, "does not match the established release")

    def test_malformed_expected_certificate_fingerprint(self):
        with self.invocation() as (root, output, environment):
            environment["RELEASE_CERT_SHA256"] = "not-a-fingerprint"
            self.assert_clean_failure(root, output, environment, "must be a 64-character")

    def test_mismatched_certificate_and_key_really_fails_signature_verification(self):
        with self.invocation(self.mismatch) as (root, output, environment):
            self.assert_clean_failure(root, output, environment, "did not verify")

    def test_probe_directory_and_staged_keystore_are_private(self):
        with self.invocation() as (root, output, environment):
            def inspect(staged, directory, _environment):
                self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o700)
                self.assertEqual(stat.S_IMODE(staged.stat().st_mode), 0o600)
            with patch.object(preflight, "validate_keystore", side_effect=inspect):
                preflight.preflight(output, environment)
            self.assertFalse(list(root.glob("hootbook-signing-preflight-*")))

    def test_each_missing_secret_fails_before_java(self):
        for name in preflight.REQUIRED:
            with self.subTest(name=name), self.invocation() as (root, output, environment):
                environment.pop(name)
                with patch.object(preflight, "validate_keystore") as validate:
                    with self.assertRaisesRegex(preflight.PreflightError, name):
                        preflight.preflight(output, environment)
                    validate.assert_not_called()
                self.assertEqual(list(root.iterdir()), [])

    def test_wrong_store_password(self):
        with self.invocation() as (root, output, environment):
            environment["KEYSTORE_PASSWORD"] = "incorrect store password"
            self.assert_clean_failure(root, output, environment, "Cannot open keystore")

    def test_wrong_key_password_for_both_formats(self):
        for fixture in (self.jks, self.pkcs12):
            with self.subTest(format=fixture.suffix), self.invocation(fixture) as (root, output, environment):
                environment["KEY_PASSWORD"] = self.store_password
                self.assert_clean_failure(root, output, environment, "Cannot decrypt the private key")

    def test_missing_alias(self):
        with self.invocation() as (root, output, environment):
            environment["KEY_ALIAS"] = "missing-private-alias"
            self.assert_clean_failure(root, output, environment, "does not identify a private-key entry")

    def test_certificate_only_alias(self):
        with self.invocation() as (root, output, environment):
            environment["KEY_ALIAS"] = "certificate-only"
            self.assert_clean_failure(root, output, environment, "does not identify a private-key entry")

    def test_malformed_base64(self):
        with self.invocation() as (root, output, environment):
            environment["KEYSTORE_BASE64"] = "not valid base64!"
            self.assert_clean_failure(root, output, environment, "not valid canonical Base64")

    def test_valid_base64_with_invalid_keystore(self):
        with self.invocation() as (root, output, environment):
            environment["KEYSTORE_BASE64"] = base64.b64encode(b"not a keystore").decode()
            self.assert_clean_failure(root, output, environment, "Cannot open keystore")

    def test_existing_file_and_symlink_are_never_overwritten(self):
        for symlink in (False, True):
            with self.subTest(symlink=symlink), self.invocation() as (root, output, environment):
                target = root / "existing-target"
                target.write_bytes(b"keep this file")
                if symlink:
                    output.symlink_to(target)
                else:
                    output.write_bytes(b"keep this file")
                with self.assertRaisesRegex(preflight.PreflightError, "already exists"):
                    preflight.preflight(output, environment)
                self.assertEqual(output.read_bytes(), b"keep this file")
                self.assertEqual(target.read_bytes(), b"keep this file")

    def test_failed_output_publication_removes_keystore(self):
        with self.invocation() as (root, output, environment):
            environment["GITHUB_OUTPUT"] = str(root / "missing-parent" / "output")
            self.assert_clean_failure(root, output, environment, "Could not securely create signing files")

    def test_cancellation_removes_probe_files(self):
        with self.invocation() as (root, output, environment):
            with patch.object(preflight, "validate_keystore", side_effect=KeyboardInterrupt):
                with self.assertRaises(KeyboardInterrupt):
                    preflight.preflight(output, environment)
            self.assertEqual(list(root.iterdir()), [])

    def test_java_argv_contains_no_secret_values(self):
        with self.invocation() as (root, output, environment):
            completed = subprocess.CompletedProcess([], 0)
            with patch.object(preflight.subprocess, "run", return_value=completed) as run:
                preflight.validate_keystore(output, root, environment)
            arguments, options = run.call_args
            for name in preflight.REQUIRED:
                self.assertNotIn(environment[name], arguments[0])
            self.assertNotIn("KEYSTORE_BASE64", options["env"])
            self.assertEqual(options["stdin"], subprocess.DEVNULL)
            self.assertEqual(options["stdout"], subprocess.DEVNULL)
            self.assertEqual(options["stderr"], subprocess.DEVNULL)

    def test_failed_signature_verification_is_a_hard_failure(self):
        with self.invocation() as (root, output, environment):
            completed = subprocess.CompletedProcess([], 16)
            with patch.object(preflight.subprocess, "run", return_value=completed):
                with self.assertRaisesRegex(preflight.PreflightError, "did not verify"):
                    preflight.preflight(output, environment)
            self.assertEqual(list(root.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
