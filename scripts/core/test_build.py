#!/usr/bin/env python3
"""Fast source-build orchestration checks. No Cargo, Gradle, SDK or emulator is run.

Run: python3 -m unittest discover -s scripts/core -p 'test_build.py' -v
"""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


build = load("core_build_tested", "build.py")
verify = load("core_verify_tested", "verify.py")


def elf(abi):
    """Minimal architecture-specific ELF with one 16 KB-aligned PT_LOAD."""
    bits, machine = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40),
                     "x86": (1, 3), "x86_64": (2, 62)}[abi]
    data = bytearray(128)
    data[:6] = b"\x7fELF" + bytes([bits, 1])
    struct.pack_into("<H", data, 18, machine)
    if bits == 2:
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, 0, 0, 128, 128, 16384)
    else:
        struct.pack_into("<I", data, 28, 52)
        struct.pack_into("<HH", data, 42, 32, 1)
        struct.pack_into("<IIIIIIII", data, 52, 1, 0, 0, 0, 128, 128, 5, 16384)
    return bytes(data)


class NativeBuildTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.module = self.root / "core-bindings"
        self.generated = self.module / "build/generated"
        self.rust = self.module / "build/rust"
        self.source = self.root / "source"
        self.sdk = self.root / "sdk"
        tools = self.sdk / "ndk" / build.LOCK["ndkVersion"] / "toolchains/llvm/prebuilt/linux-x86_64/bin"
        tools.mkdir(parents=True)
        kotlin = self.generated / "kotlin/Core.kt"
        kotlin.parent.mkdir(parents=True)
        kotlin.write_text('private fun findLibraryName(): String {\n    return "uniffi_hootkit_core"\n}\n')
        self.kotlin_bytes = kotlin.read_bytes()
        self.bindings = self.generated / "bindings-provenance.json"
        self.bindings.write_text('{"bindings": "untouched"}\n')
        self.rust.mkdir(parents=True)
        self.sentinel = self.rust / "incremental-sentinel"
        self.sentinel.write_bytes(b"keep Cargo intermediates")
        for replacement in (
            patch.object(build, "MODULE", self.module),
            patch.object(build.platform, "system", return_value="Linux"),
            patch.dict(os.environ, {"ANDROID_HOME": str(self.sdk), "CARGO": "mock-cargo"}),
        ):
            replacement.start()
            self.addCleanup(replacement.stop)
        source_check = patch.object(build, "verify_source")
        self.verify_source = source_check.start()
        self.addCleanup(source_check.stop)
        self.commands = []
        runner = patch.object(build, "run", side_effect=self.mock_run)
        self.run = runner.start()
        self.addCleanup(runner.stop)

    def mock_run(self, *args, **kwargs):
        self.commands.append(args)
        if args[0] == "mock-cargo":
            self.assertIn("--locked", args)
            self.assertIn("--release", args)
            self.assertIn("--lib", args)
            rust_target = args[args.index("--target") + 1]
            abi = next(abi for abi, pair in build.TARGETS.items() if pair[0] == rust_target)
            env = kwargs["env"]
            self.assertEqual(env["CARGO_TARGET_DIR"], str(self.rust))
            target_key = rust_target.upper().replace("-", "_")
            self.assertTrue(env[f"CARGO_TARGET_{target_key}_LINKER"].endswith(
                f"{build.TARGETS[abi][1]}{build.LOCK['minSdk']}-clang"))
            self.assertEqual(env[f"CARGO_TARGET_{target_key}_RUSTFLAGS"],
                             "-C link-arg=-Wl,-z,max-page-size=16384")
            library = self.rust / rust_target / "release/libhootkit_core.so"
            library.parent.mkdir(parents=True, exist_ok=True)
            library.write_bytes(elf(abi))
        else:
            self.assertEqual(Path(args[0]).name, "llvm-strip")
            self.assertEqual(args[1], "--strip-unneeded")
            self.assertTrue(Path(args[2]).is_file())

    def invoke(self, *args):
        with patch.object(sys, "argv", ["build.py", "--source", str(self.source), *args]), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            build.main()

    def native(self, *abis):
        self.commands.clear()
        self.invoke("--native", *(["--abis", *abis] if abis else []))
        return json.loads((self.generated / "native-provenance.json").read_text())

    def assert_coverage(self, native, abis):
        self.assertEqual(native["requestedAbis"], abis)
        self.assertEqual(list(native["libraries"]), abis)
        for key, value in build.LOCK.items():
            self.assertEqual(native[key], value)
        self.assertEqual({p.name for p in (self.generated / "jniLibs").iterdir()}, set(abis))
        self.assertEqual([c[c.index("--target") + 1] for c in self.commands if c[0] == "mock-cargo"],
                         [build.TARGETS[abi][0] for abi in abis])
        self.assertEqual(len(self.commands), 2 * len(abis))
        verify.verify_native_coverage(native, self.generated, abis)
        self.assertEqual(self.sentinel.read_bytes(), b"keep Cargo intermediates")
        self.assertEqual((self.generated / "kotlin/Core.kt").read_bytes(), self.kotlin_bytes)
        self.assertEqual(self.bindings.read_text(), '{"bindings": "untouched"}\n')

    def test_default_build_keeps_four_abis_and_checks_source_twice(self):
        self.assert_coverage(self.native(), build.LOCK["abis"])
        self.assertEqual(self.verify_source.call_count, 2)

    def test_slim_build_runs_only_arm64_and_records_actual_coverage(self):
        native = self.native("arm64-v8a")
        self.assert_coverage(native, ["arm64-v8a"])
        with self.assertRaisesRegex(AssertionError, "Requested native ABI mismatch"):
            verify.verify_native_coverage(native, self.generated, build.requested_abis())

    def test_full_slim_full_removes_stale_jni_without_deleting_cargo_outputs(self):
        self.native()
        cargo_outputs = list(self.rust.glob("*/release/libhootkit_core.so"))
        original_cargo = {path: path.read_bytes() for path in cargo_outputs}
        self.assertEqual(len(original_cargo), 4)
        stale = self.generated / "jniLibs/arm64-v8a/libhootkit_core.so"
        stale.write_bytes(b"old package basename")
        self.assert_coverage(self.native("arm64-v8a"), ["arm64-v8a"])
        self.assertFalse(stale.exists())
        self.assertEqual({path: path.read_bytes() for path in cargo_outputs}, original_cargo)
        self.assert_coverage(self.native(), build.LOCK["abis"])

    def test_subset_order_follows_lock(self):
        self.assert_coverage(self.native("x86_64", "arm64-v8a"), ["arm64-v8a", "x86_64"])

    def test_cleanup_unlinks_generated_jni_symlink_without_following_it(self):
        (self.generated / "jniLibs").symlink_to(self.rust, target_is_directory=True)
        self.assert_coverage(self.native("arm64-v8a"), ["arm64-v8a"])
        self.assertFalse((self.generated / "jniLibs").is_symlink())

    def test_invalid_requests_fail_before_build_or_cleanup(self):
        original = self.native()
        for args in [("--native", "--abis", "mips"), ("--native", "--abis"),
                     ("--abis", "arm64-v8a"), ("--native", "--verify-only", "--abis", "arm64-v8a"),
                     ("--native", "--abis", "arm64-v8a", "arm64-v8a")]:
            with self.subTest(args=args):
                self.run.reset_mock()
                with self.assertRaises((SystemExit, ValueError)):
                    self.invoke(*args)
                self.run.assert_not_called()
                self.assertEqual(json.loads((self.generated / "native-provenance.json").read_text()), original)

    def test_failed_build_invalidates_old_provenance_and_preserves_cargo(self):
        self.native()
        self.run.side_effect = subprocess.CalledProcessError(1, "mock-cargo")
        with self.assertRaises(subprocess.CalledProcessError):
            self.native("arm64-v8a")
        self.assertFalse((self.generated / "native-provenance.json").exists())
        self.assertFalse((self.generated / "jniLibs").exists())
        self.assertTrue(self.sentinel.is_file())
        self.assertEqual(len(list(self.rust.glob("*/release/libhootkit_core.so"))), 4)

    def test_concurrent_source_change_does_not_stamp_success(self):
        self.native()
        self.verify_source.side_effect = [None, ValueError("source changed")]
        with self.assertRaisesRegex(ValueError, "source changed"):
            self.native("arm64-v8a")
        self.assertFalse((self.generated / "native-provenance.json").exists())

    def test_verifier_accepts_legacy_full_provenance_but_rejects_stale_files(self):
        native = self.native()
        del native["requestedAbis"]
        verify.verify_native_coverage(native, self.generated, build.LOCK["abis"])
        stale = self.generated / "jniLibs/arm64-v8a/libhootkit_core.so"
        stale.write_bytes(b"old basename")
        with self.assertRaisesRegex(AssertionError, "Generated JNI coverage mismatch"):
            verify.verify_native_coverage(native, self.generated, build.LOCK["abis"])

    def test_gradle_tracks_slim_property_and_passes_explicit_subset(self):
        script = (build.ROOT / "core-bindings/build.gradle.kts").read_text()
        self.assertIn('val slimCoreNative = project.hasProperty("slimDebug")', script)
        self.assertIn('inputs.property("slimDebug", slimCoreNative)', script)
        self.assertIn('if (slimCoreNative) listOf("--abis", "arm64-v8a") else emptyList<String>()', script)


if __name__ == "__main__":
    unittest.main()
