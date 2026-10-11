# Android Core native packaging contract

The App owns packaging only. `core-bindings/core.lock.json`, pinned Core/Spec commits, UDL, Rust library name and Core `uniffi.toml` remain unchanged.

Pinned generated Kotlin `findLibraryName` defaults to `uniffi_hootkit_core`. The Rust build produces `libhootkit_core.so`. `scripts/core/build.py` copies that exact Rust output to the App-owned package basename **libuniffi_hootkit_core.so**, strips the copy with the pinned NDK, and hashes shipped bytes. It removes the old generated basename. Both library modules retain these already-stripped bytes through `keepDebugSymbols`. No System-property override or hand-edited generated binding is used.

Generation records the real loader name; native generation depends on bindings generation and checks that name. Bindings/native provenance include independent `packaging` fields (`libraryName`, `libraryFile`, `rustOutputFile`), separate from all original lock/source hashes. A pin/generator change that changes the default name fails until packaging is consciously updated.

`python3 scripts/core/verify.py` checks the pinned provenance, generated default loader, four JNI paths and native bytes, four-ABI AAR coverage, old-basename absence, ELF 16KB PT_LOAD, JNA and backup exclusions. `python3 scripts/core/integration/apk_check.py APK ABI_LIST` cross-checks the same generated name against actual APK paths and hashes, JNA, alignment and DEX API markers.

`core-platform` declares `androidx.test:runner:1.7.0` as **androidTestImplementation** only. A runner declaration in the manifest does not ship its class. `python3 scripts/core/verify.py --runner-apk APK` checks ZIP CRC and parses actual DEX class-definition tables for AndroidJUnitRunner; a mere descriptor/reference is insufficient. Native smoke uses real Android bindings/JNA/packaged native library and target application, never a mock host application.
