# Fail-fast signing preflight

Testing APK and Release must run `.github/actions/signing-preflight` directly
after repository checkout and JDK 21 setup, before Android SDK setup, pinned Core source
checkout and toolchain setup, dependency caches, Gradle configuration, or native/application builds.
No Android SDK, Gradle invocation, download, or network access is needed by the
preflight itself. Release dry-run skips signing preflight, matching its existing
secret-free validation path; it must not build, tag, publish, or upload a release.

The action takes these existing `KEYSTORE` environment secrets **via `env`**, not
interpolated shell text or command-line arguments:

- `KEYSTORE_BASE64`: the original release keystore, canonical padded Base64;
  optional CR/LF line wrapping is accepted, other ignored characters are not
- `KEYSTORE_PASSWORD`: password that opens the keystore
- `KEY_ALIAS`: alias of the original release private key
- `KEY_PASSWORD`: password that decrypts that key, checked independently even if
  it differs from the store password

It fails early with a safe, stage-specific error for missing secrets, malformed
Base64, invalid/unreadable JKS or PKCS12, wrong store password, missing alias,
certificate-only alias, wrong key password, unsupported key algorithm, or failed
signature creation/verification, or an unexpected release certificate. File extensions do not determine the format:
Java probes the file. A Base64 value is only encoding; opening the keystore and
unlocking its private-key entry actually validates the encrypted contents.

The JDK signs a tiny temporary payload with the selected RSA, EC or DSA private
key and verifies that signature with the same entry's certificate public key.
It does not require a CA-trusted certificate chain, so an ordinary self-signed
Android release certificate is accepted. The composite action also checks the
existing public release certificate SHA-256 fingerprint, before building:
`23cf2e8f08aa2a576591a1004fc628fa6ae7badee231854b1dee83c639cef726`.
This retains the identity already enforced by the Debug APK workflow. This does
not validate PKI certificate dates or establish Play Store acceptance. The
standalone Python script can optionally take `RELEASE_CERT_SHA256` via the
environment; the composite action always supplies the repository's expected
fingerprint, so callers cannot accidentally omit the identity check.

## Workflow integration

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: temurin
- name: Check signing before build setup
  id: signing-preflight
  uses: ./.github/actions/signing-preflight
  env:
    KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
# Only now set up the Android SDK/pinned Core toolchain and run Gradle.
- name: Build
  env:
    KEYSTORE_FILE: ${{ steps.signing-preflight.outputs.keystore-path }}
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew assembleStandardPreview --no-configuration-cache
- name: Clean up validated signing keystore
  if: always()
  env:
    KEYSTORE_FILE: ${{ steps.signing-preflight.outputs.keystore-path }}
  run: |
    if [ -n "$KEYSTORE_FILE" ]; then rm -f -- "$KEYSTORE_FILE"; fi
```

The action returns `keystore-path` only on success and writes the original bytes
to `$RUNNER_TEMP/hootbook-release.keystore`, created with permissions `0600`.
Reuse this file for all signed builds in the job. Do not pass passwords with
`-Pandroid.injected.signing.*` on Gradle's command line, echo them, run with shell
tracing, or upload/cache the keystore. Gradle must read the four signing values
from the build step's environment. Limit those variables to the steps that need
them. Never generate a replacement production key to make the check pass.

Probe keystores/payloads/signatures live in a private `0700` temporary directory
and are removed on success, error, and handled SIGINT/SIGTERM. Failed preflight
or failed action-output publication removes any new persistent output. Existing
files/symlinks are not overwritten. The job's `always()` cleanup removes the
validated keystore after use, including when a later build fails. Abrupt runner
termination/SIGKILL cannot execute cleanup; use ephemeral trusted runners and do
not rely on file deletion as secure disk erasure.

## This is key readiness, not APK verification

The temporary payload is not an APK and this preflight does not exercise Android
APK signature schemes v1/v2/v3/v4, AGP packaging, zip alignment, installability,
or an AAB upload. Before publishing built APKs, separately run Android Build
Tools `apksigner verify` on every intended APK and compare its signing
certificate fingerprint with the established release identity. Do not change an
expected fingerprint just to make an unexpected key pass. AABs need their own
JAR signing verification; `apksigner` is specifically for APKs.

## Offline regression tests

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/signing -p 'test_*.py'
```

Requires Python 3 and JDK 21 (`java` with the compiler module, plus `keytool`).
Tests create only ephemeral dummy self-signed keys in `TemporaryDirectory`,
never read production secrets, and remove all fixtures afterward. Coverage
includes JKS and PKCS12 with distinct store/key passwords, missing credentials,
malformed Base64 and invalid keystore bytes, wrong passwords/alias, a
certificate-only entry, expected/unexpected fingerprints, a real mismatched
certificate/private-key signature failure, safe argv/log handling, output
permissions, symlink refusal, cancellation cleanup, and failed output cleanup.

Official references:

- [JDK KeyStore: file probing and separately protected entries](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html)
- [JDK Signature: signing and public-key verification](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html)
- [Android APK signing and verification tools](https://developer.android.com/tools/apksigner)
- [Android app signing and certificate identity](https://developer.android.com/studio/publish/app-signing)
