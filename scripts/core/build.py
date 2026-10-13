#!/usr/bin/env python3
"""Generate bindings or build Android libraries from the exact core.lock.json source."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "core-bindings"
LOCK = json.loads((MODULE / "core.lock.json").read_text())
# App-owned package alias, matching the exact pinned UniFFI-generated Kotlin loader.
# Rust still builds its original libhootkit_core.so; Core source/config remain untouched.
LIBRARY_NAME = "uniffi_hootkit_core"
LIBRARY_FILE = f"lib{LIBRARY_NAME}.so"


def generated_library_name():
    names = set()
    for path in (MODULE / "build/generated/kotlin").rglob("*.kt"):
        body = re.search(r"private fun findLibraryName\([^)]*\): String \{(.*?)\n\}", path.read_text(), re.S)
        if body:
            names.update(re.findall(r'return "([^"\n]+)"', body.group(1)))
    if names != {LIBRARY_NAME}:
        raise ValueError(f"Generated UniFFI default loader differs from App package alias: {sorted(names)}")
    return next(iter(names))


def packaging_contract():
    return {"libraryName": generated_library_name(), "libraryFile": LIBRARY_FILE,
            "rustOutputFile": "libhootkit_core.so"}


TARGETS = {
    "arm64-v8a": ("aarch64-linux-android", "aarch64-linux-android"),
    "armeabi-v7a": ("armv7-linux-androideabi", "armv7a-linux-androideabi"),
    "x86": ("i686-linux-android", "i686-linux-android"),
    "x86_64": ("x86_64-linux-android", "x86_64-linux-android"),
}


def run(*args, **kwargs):
    print("+", " ".join(str(arg) for arg in args), flush=True)
    return subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_source(source):
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if commit != LOCK["coreCommit"]:
        raise ValueError(f"Core commit differs from core.lock.json: {commit}")
    if subprocess.check_output(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=no"], text=True).strip():
        raise ValueError("Pinned Core source has tracked modifications")
    for name, expected in LOCK["sha256"].items():
        if sha(source / name) != expected:
            raise ValueError(f"Pinned source hash differs: {name}")
    if LOCK["specCommit"] not in (source / "spec/VERSION").read_text():
        raise ValueError("Core spec source differs from core.lock.json")
    match = re.search(r'\[\[package\]\]\s+name = "uniffi"\s+version = "([^"]+)"', (source / "Cargo.lock").read_text())
    if not match or match.group(1) != LOCK["uniffiVersion"]:
        raise ValueError("UniFFI version differs from core.lock.json")
    rustc = os.environ.get("RUSTC", "rustc")
    version = subprocess.check_output([rustc, "--version"], text=True).split()[1]
    if version != LOCK["rustVersion"]:
        raise ValueError(f"Use pinned Rust {LOCK['rustVersion']}; found {version}")


def verify_elf(path, abi):
    data = path.read_bytes()
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError(f"Not a little-endian Android ELF: {path}")
    bits = data[4]
    machine = struct.unpack_from("<H", data, 18)[0]
    expected = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86": (1, 3), "x86_64": (2, 62)}[abi]
    if (bits, machine) != expected:
        raise ValueError(f"ELF architecture differs: {abi}")
    if bits == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        entry_size, count = struct.unpack_from("<HH", data, 54)
        fmt, align_index = "<IIQQQQQQ", 7
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        entry_size, count = struct.unpack_from("<HH", data, 42)
        fmt, align_index = "<IIIIIIII", 7
    alignments = []
    for index in range(count):
        header = struct.unpack_from(fmt, data, offset + index * entry_size)
        if header[0] == 1:
            align = header[align_index]
            file_offset = header[2] if bits == 2 else header[1]
            virtual_address = header[3] if bits == 2 else header[2]
            if align < 16384 or (virtual_address - file_offset) % 16384:
                raise ValueError(f"PT_LOAD is not 16 KB aligned: {abi}")
            alignments.append(align)
    if not alignments:
        raise ValueError("ELF has no PT_LOAD segments")
    return {"sha256": sha(path), "loadAlignments": alignments, "bytes": len(data)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT.parent / "hootkit-core")
    parser.add_argument("--native", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    source = args.source.resolve()
    verify_source(source)
    if args.verify_only:
        print("PASS: pinned Core / Spec / UniFFI / Rust source")
        return
    target = MODULE / "build/rust"
    generated = MODULE / "build/generated"
    env = dict(os.environ, CARGO_TARGET_DIR=str(target))
    cargo = os.environ.get("CARGO", "cargo")
    provenance = dict(LOCK)
    if not args.native:
        run(cargo, "build", "--manifest-path", source / "Cargo.toml", "--locked", "--features", "bindgen",
            "--bin", "uniffi-bindgen", env=env)
        out = generated / "kotlin"
        out.mkdir(parents=True, exist_ok=True)
        run(target / "debug/uniffi-bindgen", "generate", source / "src/hootkit_core.udl", "--language", "kotlin",
            "--config", source / "uniffi.toml", "--out-dir", out, "--no-format")
        provenance["bindings"] = {str(p.relative_to(out)): sha(p) for p in out.rglob("*.kt")}
        provenance["packaging"] = packaging_contract()
        destination = generated / "bindings-provenance.json"
    else:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            raise ValueError("Set ANDROID_HOME or ANDROID_SDK_ROOT")
        ndk = Path(sdk) / "ndk" / LOCK["ndkVersion"]
        host = {"Darwin": "darwin-x86_64", "Linux": "linux-x86_64"}.get(platform.system())
        if not host:
            raise ValueError("Native build script currently supports macOS and Linux hosts")
        tools = ndk / "toolchains/llvm/prebuilt" / host / "bin"
        if not tools.is_dir():
            raise ValueError(f"Install pinned NDK {LOCK['ndkVersion']}")
        provenance["packaging"] = packaging_contract()
        libraries = {}
        for abi in LOCK["abis"]:
            rust_target, clang_target = TARGETS[abi]
            cc = tools / f"{clang_target}{LOCK['minSdk']}-clang"
            arch_env = dict(env)
            arch_env["CARGO_TARGET_" + rust_target.upper().replace("-", "_") + "_LINKER"] = str(cc)
            arch_env["CC_" + rust_target.replace("-", "_")] = str(cc)
            arch_env["AR_" + rust_target.replace("-", "_")] = str(tools / "llvm-ar")
            arch_env["CARGO_TARGET_" + rust_target.upper().replace("-", "_") + "_RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384"
            run(cargo, "build", "--manifest-path", source / "Cargo.toml", "--locked", "--release", "--lib",
                "--target", rust_target, env=arch_env)
            out = generated / "jniLibs" / abi
            out.mkdir(parents=True, exist_ok=True)
            # A stale previous package basename must not coexist with the generated loader alias.
            (out / "libhootkit_core.so").unlink(missing_ok=True)
            library = out / LIBRARY_FILE
            shutil.copy2(target / rust_target / "release/libhootkit_core.so", library)
            run(tools / "llvm-strip", "--strip-unneeded", library)
            libraries[abi] = verify_elf(library, abi)
        provenance["libraries"] = libraries
        destination = generated / "native-provenance.json"
    # A concurrent checkout/edit must not be stamped as the originally verified source.
    verify_source(source)
    generated.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(provenance, indent=2) + "\n")
    print(f"PASS: {destination}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as failure:
        print(f"Core build failed: {failure}", file=sys.stderr)
        sys.exit(1)
