#!/usr/bin/env python3
"""Check an actual final APK for Core/JNA ABIs, exact Core bytes and ELF 16 KB alignment."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("core_build", ROOT / "scripts/core/build.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


def main():
    apk = Path(sys.argv[1]).resolve()
    expected = set(sys.argv[2].split(","))
    native = json.loads((ROOT / "core-bindings/build/generated/native-provenance.json").read_text())
    contract = build.packaging_contract()
    assert native.get("packaging") == contract, "Generated loader/native provenance contract mismatch"
    filename = contract["libraryFile"]
    checks = []
    with tempfile.TemporaryDirectory() as temporary, zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        core_abis = {n.split("/")[1] for n in names if re.fullmatch(r"lib/[^/]+/" + re.escape(filename), n)}
        assert not any(n.endswith("/libhootkit_core.so") for n in names), "Stale Core library basename packaged"
        jna_abis = {n.split("/")[1] for n in names if re.fullmatch(r"lib/[^/]+/libjnidispatch\.so", n)}
        assert core_abis == jna_abis == expected, (core_abis, jna_abis, expected)
        print("PASS generated default UniFFI loader/native APK paths:", contract["libraryName"], filename)
        print("PASS final APK Core and JNA ABI coverage:", ",".join(sorted(expected)))
        for name in names:
            if not re.fullmatch(r"lib/[^/]+/[^/]+\.so", name):
                continue
            abi = name.split("/")[1]
            data = archive.read(name)
            if name.endswith("/" + filename):
                assert hashlib.sha256(data).hexdigest() == native["libraries"][abi]["sha256"], f"Core bytes differ: {name}"
            # Core is built with 16 KB on every ABI; 16 KB page devices use 64-bit ABIs.
            if abi in ["arm64-v8a", "x86_64"] or name.endswith("/" + filename):
                target = Path(temporary) / Path(name).name
                target.write_bytes(data)
                info = build.verify_elf(target, abi)
                checks.append({"entry": name, "compression": archive.getinfo(name).compress_type, **info})
        dex = b"".join(archive.read(n) for n in names if re.fullmatch(r"classes\d*\.dex", n))
        # The exact compiled default loader string must be present, not merely a FFI symbol prefix.
        literal = contract["libraryName"].encode("ascii")
        assert len(literal) < 128 and bytes([len(literal)]) + literal + b"\0" in dex, "Compiled UniFFI default loader literal missing"
        for marker in [b"Ldev/hootkit/core/HootKitCore;", b"Lcom/sun/jna/Native;", b"Ldev/hootkit/core/UniffiLib;", b"Ldev/hootkit/core/ChangeListener;", b"Ldev/hootkit/core/Logger;"]:
            assert marker in dex, f"Generated API or JNA callback reflection marker missing: {marker}"
        print("PASS final APK generated API, UniFFI native interface and JNA/callback DEX markers")
    print("PASS final APK exact pinned Core bytes and every packaged 64-bit ELF 16 KB PT_LOAD alignment")
    record = {"apk": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "expected_abis": sorted(expected), "native_checks": checks, "dex_markers_verified": True, "generated_loader_native_packaging": contract, "device_execution": False}
    output = ROOT / "build/core-adoption-integration-audit" / (apk.stem + "-native-check.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(record, indent=2) + "\n")
    print("Record:", output)


if __name__ == "__main__":
    main()
