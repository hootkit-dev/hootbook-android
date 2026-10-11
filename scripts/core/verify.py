#!/usr/bin/env python3
"""Check packaged Core provenance, ABI coverage and Android backup exclusions."""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("core_build", ROOT / "scripts/core/build.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def dex_class_definitions(data):
    """Read actual DEX class_defs (not a descriptor appearing only as a type reference)."""
    require(data[:4] == b"dex\n", "Not a DEX file")
    string_count, string_offset = struct.unpack_from("<II", data, 56)
    type_count, type_offset = struct.unpack_from("<II", data, 64)
    class_count, class_offset = struct.unpack_from("<II", data, 96)
    strings = []
    for index in range(string_count):
        offset = struct.unpack_from("<I", data, string_offset + index * 4)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1  # skip ULEB128 UTF-16 length; descriptors are ASCII MUTF-8.
        strings.append(data[offset:data.index(b"\0", offset)].decode("utf-8", errors="replace"))
    types = [strings[struct.unpack_from("<I", data, type_offset + index * 4)[0]] for index in range(type_count)]
    return {types[struct.unpack_from("<I", data, class_offset + index * 32)[0]] for index in range(class_count)}


def verify_runner(apk):
    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, "Instrumentation APK CRC failure")
        classes = set()
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex"):
                classes.update(dex_class_definitions(archive.read(name)))
    runner = "Landroidx/test/runner/AndroidJUnitRunner;"
    require(runner in classes, "AndroidJUnitRunner class definition missing from instrumentation APK")
    print(f"PASS: actual AndroidJUnitRunner DEX class definition / APK CRC: {apk}")


def verify_native_coverage(native, directory, abis):
    expected = set(abis)
    # Older full builds have no requestedAbis field. Only their full locked set is valid.
    require(native.get("requestedAbis", build.LOCK["abis"]) == abis, "Requested native ABI mismatch")
    require(set(native["libraries"]) == expected, "Native ABI coverage mismatch")
    filename = native["packaging"]["libraryFile"]
    actual = {path.relative_to(directory / "jniLibs").as_posix()
              for path in (directory / "jniLibs").rglob("*.so")}
    require(actual == {f"{abi}/{filename}" for abi in abis}, "Generated JNI coverage mismatch")
    for abi, info in native["libraries"].items():
        require(build.verify_elf(directory / "jniLibs" / abi / filename, abi) == info,
                f"Native library mismatch: {abi}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runner-apk", type=Path)
    parser.add_argument("--abis", nargs="+", choices=build.LOCK["abis"],
                        help="Expected native ABIs (default: every locked ABI)")
    args = parser.parse_args()
    if args.runner_apk:
        verify_runner(args.runner_apk)
        return
    abis = build.requested_abis(args.abis)
    directory = ROOT / "core-bindings/build/generated"
    bindings = json.loads((directory / "bindings-provenance.json").read_text())
    native = json.loads((directory / "native-provenance.json").read_text())
    lock = build.LOCK
    contract = build.packaging_contract()
    require(bindings.get("packaging") == native.get("packaging") == contract, "Generated loader/native packaging contract mismatch")
    filename = contract["libraryFile"]
    for field in lock:
        require(bindings[field] == native[field] == lock[field], f"Provenance mismatch: {field}")
    for name, sha in bindings["bindings"].items():
        require(build.sha(directory / "kotlin" / name) == sha, "Generated binding hash mismatch")
    verify_native_coverage(native, directory, abis)
    print(f"PASS: generated loader {contract['libraryName']} matches native paths {filename}: {', '.join(abis)}")
    print("PASS: same pinned source for Kotlin and requested Android ABI libraries; ELF 16 KB alignment")

    aar = ROOT / "core-bindings/build/outputs/aar/core-bindings-debug.aar"
    with zipfile.ZipFile(aar) as archive:
        actual = {name.split("/")[1] for name in archive.namelist()
                  if name.startswith("jni/") and name.endswith("/" + filename)}
        require(not any(name.endswith("/libhootkit_core.so") for name in archive.namelist()), "Stale native library basename packaged")
        require(actual == set(abis), "Packaged ABI coverage mismatch")
        for abi, info in native["libraries"].items():
            require(hashlib.sha256(archive.read(f"jni/{abi}/{filename}")).hexdigest() == info["sha256"],
                    f"AAR library hash mismatch: {abi}")
        with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as classes:
            require("dev/hootkit/core/HootKitCore.class" in classes.namelist(), "Generated API absent from AAR")
        require("proguard.txt" in archive.namelist(), "Consumer R8 rules absent")
    print("PASS: bindings AAR contains generated API, exact requested native libraries and consumer rules")

    candidates = list((Path.home() / ".gradle/caches/modules-2/files-2.1/net.java.dev.jna/jna/5.17.0").glob("*/*.aar"))
    require(bool(candidates), "JNA 5.17.0 AAR not resolved")
    with tempfile.TemporaryDirectory() as temporary, zipfile.ZipFile(candidates[0]) as archive:
        for abi in lock["abis"]:
            require(f"jni/{abi}/libjnidispatch.so" in archive.namelist(), f"JNA ABI missing: {abi}")
        for abi in ["arm64-v8a", "x86_64"]:
            path = Path(temporary) / f"{abi}.so"
            path.write_bytes(archive.read(f"jni/{abi}/libjnidispatch.so"))
            build.verify_elf(path, abi)
    print("PASS: JNA AAR covers four ABIs; both 64-bit libraries are ELF 16 KB aligned")

    expected = {("database", name) for name in ["hootkit.sqlite", "hootkit.sqlite-wal", "hootkit.sqlite-shm"]}
    expected.add(("file", "hootkit-core/"))
    legacy = ET.parse(ROOT / "app/src/main/res/xml/backup_rules.xml").getroot()
    modern = ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
    for name, rules in [("Android <=30", legacy), ("cloud-backup", modern.find("cloud-backup")),
                        ("device-transfer", modern.find("device-transfer"))]:
        require(rules is not None, f"Missing backup rules: {name}")
        excludes = {(node.get("domain"), node.get("path")) for node in rules.findall("exclude")}
        require(expected <= excludes, f"Missing Core backup exclusions: {name}")
    print("PASS: database/WAL/SHM exclusions cover legacy backup, cloud backup and device transfer")


if __name__ == "__main__":
    main()
