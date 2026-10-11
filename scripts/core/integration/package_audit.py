#!/usr/bin/env python3
"""Aggregate per-agent source snapshots into a guarded, incremental round rollback.

Only execute after the coordinator freezes all writers. Does not roll back first-batch
Core modules or user-owned app_adapt.md. Builds and working-tree changes are not inferred
from Git HEAD; each agent's saved round baseline is authoritative.
"""
import difflib
import hashlib
import json
from pathlib import Path
import shutil
import tarfile

ROOT = Path(__file__).resolve().parents[3]
AUDIT = ROOT / "build/core-adoption-integration-audit"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    files = {}

    def add(path, original=None, expected=None):
        assert path not in files, f"Overlapping agent scope: {path}"
        target = ROOT / path
        assert target.is_file(), f"Source absent: {target}"
        if expected:
            assert sha(target) == expected, f"Agent snapshot stale; request refresh: {target}"
        before = AUDIT / "baseline" / path
        if original:
            before.parent.mkdir(parents=True, exist_ok=True)
            if original != before:
                shutil.copy2(original, before)
        files[path] = {"existed": original is not None, "baseline_sha256": sha(before) if original else None, "modified_sha256": sha(target)}
        after = AUDIT / "modified" / path
        after.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, after)

    host = ROOT / "build/core-adoption-host-audit"
    for path, metadata in json.loads((host / "baseline.json").read_text()).items():
        add(path, host / "original" / path if metadata["existed"] else None, metadata["modified_sha256"])
    accounts = ROOT / "build/core-adoption-audit/accounts-categories"
    for metadata in json.loads((accounts / "manifest.json").read_text())["files"]:
        assert metadata["baseline_sha256"] is None, "Review feature baseline format before rollback"
        add(metadata["path"], expected=metadata["modified_sha256"])
    ledger = ROOT / "build/core-ui-ledger-audit"
    for metadata in json.loads((ledger / "manifest.json").read_text()):
        assert metadata["baseline_sha256"] is None, "Review ledger baseline format before rollback"
        add(metadata["path"], expected=metadata["modified_sha256"])
    for metadata in json.loads((AUDIT / "ci-baseline.json").read_text()):
        path = metadata["path"]
        assert sha(AUDIT / "baseline" / path) == metadata["sha256"]
        add(path, AUDIT / "baseline" / path)
    for directory in [ROOT / ".github/actions/setup-core", ROOT / "scripts/core/integration"]:
        for path in sorted(directory.rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                add(str(path.relative_to(ROOT)))

    patch = []
    for path, metadata in files.items():
        old = (AUDIT / "baseline" / path).read_text().splitlines(True) if metadata["existed"] else []
        new = (AUDIT / "modified" / path).read_text().splitlines(True)
        patch.extend(difflib.unified_diff(old, new, fromfile="a/" + path if old else "/dev/null", tofile="b/" + path))
    (AUDIT / "changes.patch").write_text("".join(patch))
    with tarfile.open(AUDIT / "modified.tar.gz", "w:gz") as archive:
        for path in sorted(files):
            archive.add(AUDIT / "modified" / path, arcname=path, recursive=False)
    preserved = {}
    for path in ["app_adapt.md", "settings.gradle.kts", "build.gradle.kts", "gradle/libs.versions.toml", "core-bindings/core.lock.json"]:
        assert path not in files
        preserved[path] = sha(ROOT / path)
    # Protect ALL first-batch source paths except the explicitly incremental workflow/docs files.
    first = json.loads((ROOT / "build/core-adoption-audit/manifest.json").read_text())
    candidates = first.get("files", first) if isinstance(first, dict) else first
    if isinstance(candidates, list):
        for item in candidates:
            path = item.get("path") if isinstance(item, dict) else item
            if path and path not in files and (ROOT / path).is_file():
                preserved[path] = sha(ROOT / path)
    manifest = {"root": str(ROOT), "scope": "Only this round; preserves first-batch infrastructure and user app_adapt.md", "files": files, "preserved": preserved}
    (AUDIT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    # Saved runner remains available when tracked round scripts are temporarily rolled back.
    shutil.copy2(Path(__file__).with_name("run_record.py"), AUDIT / "run_record.py")
    (AUDIT / "rollback.py").write_text(ROLLBACK)
    print(f"PASS packaged {len(files)} incremental files; protected {len(preserved)} baseline files")


ROLLBACK = '''#!/usr/bin/env python3
import hashlib, json, pathlib, shutil, sys
audit = pathlib.Path(__file__).resolve().parent
manifest = json.loads((audit / "manifest.json").read_text())
root = pathlib.Path(manifest["root"])
mode = sys.argv[1] if len(sys.argv) > 1 else "--check"
assert mode in ["--check", "--apply", "--reapply", "--check-baseline"]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
for path, expected in manifest["preserved"].items():
    assert sha(root / path) == expected, f"Preserved state changed: {path}"
for path, metadata in manifest["files"].items():
    target = root / path
    baseline = mode in ["--reapply", "--check-baseline"]
    if baseline and not metadata["existed"]:
        assert not target.exists(), f"New file still present: {path}"
    else:
        expected = metadata["baseline_sha256"] if baseline else metadata["modified_sha256"]
        assert target.is_file() and sha(target) == expected, f"Source changed; preserve before rollback: {path}"
for path, metadata in manifest["files"].items():
    target = root / path
    if mode == "--apply":
        if metadata["existed"]: shutil.copy2(audit / "baseline" / path, target)
        else: target.unlink()
    elif mode == "--reapply":
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(audit / "modified" / path, target)
print(f"PASS {mode}: {len(manifest['files'])} round files; {len(manifest['preserved'])} preserved files untouched")
'''


if __name__ == "__main__":
    main()
