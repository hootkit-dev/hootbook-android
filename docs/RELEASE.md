# Release Process

This document describes how to create releases for Hootbook.

## Prerequisites

### 1. Create Signing Keystore (One-time setup)
If you don't have a keystore yet:
```bash
keytool -genkey -v -keystore release.keystore \
  -alias hootbook -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Add GitHub Secrets
Go to GitHub → Settings → Secrets and variables → Actions

Add these secrets:
- `KEYSTORE_BASE64`: Your keystore file encoded as base64
  ```bash
  base64 -i release.keystore | pbcopy  # Mac
  base64 release.keystore | xclip -selection clipboard  # Linux
  ```
- `KEYSTORE_PASSWORD`: Password for your keystore
- `KEY_ALIAS`: Key alias (e.g., "hootbook")
- `KEY_PASSWORD`: Password for the key

## Release Workflow

### 1. Use Conventional Commits
Start using conventional commit format:
```bash
git commit -m "feat: add new bank parser"
git commit -m "fix: resolve parsing error"
git commit -m "docs: update README"
```

The template will help you:
```bash
git commit  # Opens editor with template
```

### 2. Create a Release

#### Option A: Manual Version Selection
1. Go to [GitHub Actions](../../actions)
2. Click on "Release" workflow
3. Click "Run workflow"
4. Select version bump type:
   - `patch`: Bug fixes only (2.1.7 → 2.1.8)
   - `minor`: New features (2.1.7 → 2.2.0)
   - `major`: Breaking changes (2.1.7 → 3.0.0)
   - `auto`: Analyze commits (works after using conventional commits)
5. Click "Run workflow"

#### Option B: Test with Dry Run
1. Same as above but check "Dry run" checkbox
2. This will preview the release without creating it

### 3. What Happens Automatically

The workflow will:
1. Calculate next version based on your selection
2. Generate changelog from recent commits
3. Update version in `app/build.gradle.kts`
4. Build signed APK
5. Create git tag (e.g., `v2.2.0`)
6. Create GitHub Release with:
   - Changelog as description
   - APK attached for download
   - SHA256 checksum file

### 4. Release Notes

The workflow automatically generates release notes from your commits:
```markdown
## Changes since v2.1.7
- feat: add ICICI Bank support (abc123)
- fix: resolve download issues (def456)
- docs: update README (ghi789)
```

## Version Strategy

- **Patch** (2.1.7 → 2.1.8): Bug fixes, minor improvements
- **Minor** (2.1.7 → 2.2.0): New features, significant improvements
- **Major** (2.1.7 → 3.0.0): Breaking changes, major overhaul

## Testing Releases

Always test with dry run first:
1. Check "Dry run" when running workflow
2. Review the output in Actions logs
3. If everything looks good, run without dry run

## Troubleshooting

### Keystore Issues
If you see "unsigned APK" in the workflow:
- Verify GitHub secrets are set correctly
- Check keystore is properly base64 encoded
- Ensure passwords are correct

### Version Conflicts
If version already exists:
- Manually update version in `app/build.gradle.kts`
- Commit and push
- Try release again

### Workflow Fails
Check the Actions tab for detailed logs:
- Build errors → Fix code issues
- Signing errors → Check secrets
- Git errors → Ensure you're on main branch

## Future Improvements

Once comfortable with the process:
1. Add Play Store deployment
2. Automate version selection with semantic-release
3. Add automated testing before release
4. Include release notes in multiple languages