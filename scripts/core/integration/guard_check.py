#!/usr/bin/env python3
"""Production has one Core engine in every variant. Acceptance switches never enable legacy code."""
from pathlib import Path
root=Path(__file__).resolve().parents[3]
gradle=(root/'app/build.gradle.kts').read_text();activity=(root/'app/src/main/java/io/github/hootkit_dev/hootbook/MainActivity.kt').read_text();app=(root/'app/src/main/java/io/github/hootkit_dev/hootbook/HootbookApplication.kt').read_text()
assert 'CORE_ONLY' not in gradle and 'legacyComponentsEnabled' not in gradle
assert 'CoreAppRoot' in activity and 'CoreAppBootstrap' in app
assert 'data.repository' not in activity and 'data.sync' not in app and 'CloudCredentialStore' not in app
assert 'CoreProcess.isCrashProcess' in app and 'ProcessLifecycleOwner' in app
assert 'androidx.room' not in gradle and 'room.schemaLocation' not in gradle and 'implementation(libs.play.services.auth)' not in gradle
manifest=(root/'app/src/main/AndroidManifest.xml').read_text(); assert 'legacyComponentsEnabled' not in manifest
paths=(root/'app/src/main/res/xml/file_paths.xml').read_text();assert 'path="."' not in paths and 'core-attachment-share/' in paths and 'core-crash-share/' in paths
assert 'CORE_ACCEPTANCE", "false"' in gradle and 'coreAcceptance requires' not in gradle # field defaults off in all ordinary builds
print('PASS: production sole Core/default all variants; crash skip; lifecycle; direct Room/GMS removed; narrow shares; explicit isolated debug acceptance')
