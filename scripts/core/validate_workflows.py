#!/usr/bin/env python3
"""Android-only source-pin and signing checks. Does not read credentials or compile Rust."""
from pathlib import Path
import json
import re
import subprocess
import tempfile
import yaml
ROOT = Path(__file__).resolve().parents[2]
files = list((ROOT / '.github/workflows').glob('*.yml'))
checks = 0
for path in files:
    doc = yaml.safe_load(path.read_text())
    for job in doc.get('jobs', {}).values():
        for step in job.get('steps', []):
            if str(step.get('uses', '')).startswith('actions/cache@'):
                paths = str(step.get('with', {}).get('path', ''))
                assert not any(s in paths for s in ['build/rust', '.core-source', 'gradle-build-cache', '.cargo/git', '/target']), (path, paths)
                checks += 1
            if 'run' in step:
                script = re.sub(r'\$\{\{.*?\}\}', 'FIXTURE', step['run'])
                with tempfile.NamedTemporaryFile('w', suffix='.sh') as f:
                    f.write(script); f.flush()
                    subprocess.run(['bash', '-n', f.name], check=True)
                checks += 1
for name in ['testing-apk.yml','release.yml']:
    doc = yaml.safe_load((ROOT/'.github/workflows'/name).read_text())
    job = next(j for j in doc['jobs'].values() if any(s.get('id') == 'signing-preflight' for s in j.get('steps', [])))
    steps = job['steps']; pre = next(i for i,s in enumerate(steps) if s.get('id')=='signing-preflight')
    java = next(i for i,s in enumerate(steps) if str(s.get('uses','')).startswith('actions/setup-java@'))
    core = next(i for i,s in enumerate(steps) if s.get('uses')=='./.github/actions/setup-core')
    assert java < pre < core
    if name == 'release.yml':
        assert steps[pre].get('if') == '${{ !inputs.dry_run }}', 'dry-run must not require release signing secrets'
        checks += 1
    assert any(s.get('name')=='Verify package and release signer' for s in steps)
    for step in steps:
        if 'gradlew' in step.get('run','') and ('assemble' in step['run'] or 'bundle' in step['run']):
            assert step.get('env',{}).get('KEYSTORE_FILE')=='${{ steps.signing-preflight.outputs.keystore-path }}'
            assert 'injected.signing' not in step['run']
    checks += 3
lock = json.loads((ROOT/'core-bindings/core.lock.json').read_text())
assert lock['coreCommit']=='9ca2916045e5d5145776ec75f2ea723e022f0a61'
assert lock['specCommit']=='e662bb9c876c52d4672e80da5977bc7e32c664f9'
assert 'sdk.py' not in (ROOT/'core-bindings/build.gradle.kts').read_text()
assert 'sdk.py' not in (ROOT/'.github/actions/setup-core/action.yml').read_text()
assert 'coreVersion=' not in (ROOT/'gradle.properties').read_text()
checks += 5
print(f'PASS: {checks} source-pin, signing-order, cache-privacy and shell checks; no Android build was run.')
