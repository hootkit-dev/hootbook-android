# Standard build

The app has one product flavor, `standard`, with Firebase authentication and Firestore v2 sync.
It supports offline bookkeeping before Firebase is configured or a user signs in.

```bash
./gradlew :app:compileStandardDebugKotlin :app:testStandardDebugUnitTest
./gradlew :app:compileStandardDebugAndroidTestKotlin
./gradlew :app:assembleStandardDebug
./gradlew :app:lintStandardDebug
```

The Core source directory must be supplied by `hootkitCoreDir` (or `HOOTKIT_CORE_DIR`) and
match `core-bindings/core.lock.json`. Build types are debug, release, preview, benchmark and
profiling. Ordinary builds use Core; `coreAcceptance` is reserved for isolated debug fixtures.
See [Core integration](core-integration.md) and [publish channels](publish-channels.md).
