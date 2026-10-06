# Releasing the Android app

## What ships, and when

| Event | What CI does (`.github/workflows/build-release.yml`) |
|---|---|
| Pull request | `build`: unit tests, lint, debug APK; signed release APK/AAB for same-repo branches |
| Merge (push) to `main` | `build`, then **`deploy-play`** uploads that build's AAB to the **Play internal track** (`fastlane internal`). `deploy-play` has `needs: build`, so a failing test or lint run never ships. |
| Manual run (`workflow_dispatch`) on `main` | Same as a push to `main` (rebuild + internal deploy). No tag, no GitHub Release. |
| Push of tag `vX.Y.Z` | `build`, then **`release`**: creates a GitHub Release with the APKs/AAB. The job fails if the tag isn't exactly `v` + `versionName`. |

Promotion beyond internal testing is manual: Play Console, or `fastlane closed` /
`fastlane production` (see `fastlane/Fastfile`).

## Versioning

Every PR bumps both values in `app/build.gradle.kts`:

- `versionCode`: +1. Play rejects an upload that reuses a code, so a merge without
  a bump fails `deploy-play`.
- `versionName`: patch bump by default (`0.9.87` → `0.9.88`).

Dependabot PRs don't bump the version; add the bump before merging one.

## Tags

Tag only a build that reached Play, and use the exact `versionName`:

```bash
git tag v0.9.88 <merge-commit-on-main>
git push origin v0.9.88
```

Older tags (`v0.1.0` … `v1.5.0`, `v0.7.0`, `v20260209-025445`) predate this scheme
and don't match `versionName`. They are left in place (history is not rewritten);
new tags start at `v0.9.88`. Manual workflow runs no longer create date tags.
