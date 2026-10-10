#!/usr/bin/env bash
# Copies the Android skills Hootbook uses from https://github.com/android/skills (Apache-2.0)
# into .claude/skills/, replacing what is there. Usage:
#   scripts/update-android-skills.sh [ref]     # ref defaults to main
set -euo pipefail

REF="${1:-main}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/.claude/skills"

# Skills that fit this app. Camera, Wear OS, TV, XR, Cast, Engage, Play Billing, on-device GenAI,
# verified e-mail, restore credentials and XML-to-Compose migration are left out.
SKILLS=(
  build-system/agp/agp-9-upgrade
  device-ai/appfunctions
  devtools/android-cli
  jetpack-compose/adaptive
  jetpack-compose/theming/styles
  navigation/navigation-3
  navigation/navigation-event
  performance/r8-analyzer
  play/play-policy-insights
  profilers/android-profiler
  security/android-intent-security
  security/android-permissions-security
  system/edge-to-edge
  testing/testing-setup
)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git clone --quiet --depth 1 --branch "$REF" https://github.com/android/skills "$TMP/skills" 2>/dev/null \
  || { git clone --quiet https://github.com/android/skills "$TMP/skills" && git -C "$TMP/skills" checkout --quiet "$REF"; }
COMMIT="$(git -C "$TMP/skills" rev-parse HEAD)"

mkdir -p "$DEST"
for path in "${SKILLS[@]}"; do
  name="$(basename "$path")"
  rm -rf "${DEST:?}/$name"
  cp -R "$TMP/skills/$path" "$DEST/$name"
  # Each SKILL.md points at LICENSE.txt next to it.
  cp "$TMP/skills/LICENSE.txt" "$DEST/$name/LICENSE.txt"
done

{
  echo "# Android skills"
  echo
  echo "Copied from [android/skills](https://github.com/android/skills) at \`$COMMIT\` by"
  echo "\`scripts/update-android-skills.sh\` (Apache License 2.0, see each skill's \`LICENSE.txt\`)."
  echo "Do not edit them here; rerun the script to update. The list of skills is in the script."
  echo
  for path in "${SKILLS[@]}"; do echo "- \`$(basename "$path")\` ($path)"; done
} > "$DEST/README.md"

echo "Android skills updated to $COMMIT"
