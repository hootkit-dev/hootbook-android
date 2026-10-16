#!/usr/bin/env python3
"""Fetch prebuilt HootKit Core Android AAR from GitHub Releases or local build."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
MODULE = ROOT / "core-bindings"
LOCK_FILE = MODULE / "core.lock.json"
DEFAULT_DEST = MODULE / "libs/hootkit-core-android.aar"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def download_github_release_asset(repo: str, tag: str, token: str, dest: Path) -> str:
    headers = {
        "User-Agent": "Hootbook-CI",
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
    }

    url = f"https://api.github.com/repos/{repo}/releases/tags/{tag}"
    print(f"Querying release: {url}")
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
    except urllib.error.HTTPError:
        raise

    assets = data.get("assets", [])
    aar_asset = next((a for a in assets if a["name"].endswith(".aar")), None)
    if not aar_asset:
        raise RuntimeError(f"No .aar asset found in release {tag}: {[a['name'] for a in assets]}")

    asset_url = aar_asset["url"]
    print(f"Downloading AAR asset: {aar_asset['name']} ({aar_asset['size']} bytes)...")
    download_headers = {
        "User-Agent": "Hootbook-CI",
        "Authorization": f"Bearer {token}",
        "Accept": "application/octet-stream",
    }

    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(asset_url, headers=download_headers)
    with urllib.request.urlopen(req) as resp, dest.open("wb") as out:
        shutil.copyfileobj(resp, out)

    checksum = sha256_file(dest)
    print(f"Downloaded: {dest} (SHA-256: {checksum})")
    return checksum


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="hootkit-dev/hootkit-core", help="GitHub repo")
    parser.add_argument("--tag", default=None, help="Release tag (defaults to core.lock.json)")
    parser.add_argument("--token", default=None, help="GitHub token (defaults to CORE_REPO_READ_TOKEN or GITHUB_TOKEN)")
    parser.add_argument("--dest", type=Path, default=DEFAULT_DEST, help="Destination AAR path")
    parser.add_argument("--expected-sha256", default=None, help="Expected SHA256 checksum")
    args = parser.parse_args()

    lock = json.loads(LOCK_FILE.read_text()) if LOCK_FILE.exists() else {}
    version = lock.get("coreVersion", "0.2.0")
    tag = args.tag or lock.get("coreReleaseTag") or f"v{version}"
    token = args.token or os.environ.get("CORE_REPO_READ_TOKEN") or os.environ.get("GITHUB_TOKEN")
    dest = args.dest.resolve()

    # 1. Check if destination already exists and is valid
    if dest.exists() and dest.stat().st_size > 0:
        current_sha = sha256_file(dest)
        expected = args.expected_sha256 or lock.get("aarSha256")
        if not expected or current_sha == expected:
            print(f"Using existing AAR: {dest} (SHA-256: {current_sha})")
            return

    # 2. Check local sibling repository (for local dev without downloading)
    local_aar = ROOT.parent / "hootkit-core/target/android-release"
    if local_aar.is_dir():
        versioned = local_aar / f"hootkit-core-android-{version}.aar"
        candidates = [versioned] if versioned.is_file() else sorted(local_aar.glob("*.aar"), reverse=True)
        if candidates:
            src = candidates[0]
            print(f"Copying from local core build: {src} -> {dest}")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dest)
            return

    # 3. Download from GitHub Release
    if not token:
        if dest.exists():
            print(f"Warning: No token provided, using existing {dest}")
            return
        raise RuntimeError("No token provided (set CORE_REPO_READ_TOKEN or --token) and no local AAR found.")

    download_github_release_asset(args.repo, tag, token, dest)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error fetching Core release: {e}", file=sys.stderr)
        sys.exit(1)
