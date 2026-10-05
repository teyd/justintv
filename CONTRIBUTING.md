# Contributing

## Build and check

Install [mise](https://mise.jdx.dev), then:

```sh
mise install
mise run android:setup
./gradlew :app:assembleDebug testDebugUnitTest spotlessCheck lint
```

Debug APKs are under `app/build/outputs/apk/debug/`. For login/Helix testing,
put your Twitch application's public `TWITCH_CLIENT_ID` in gitignored `.env.local`.
Never add client secrets, tokens, signing keys, or credentials to Git.

## Branches and pull requests

- Start a focused, short-lived branch from current `main`: `feat/...`, `fix/...`,
  or `chore/...`. There is no permanent `develop` branch.
- Open a PR, review the diff, and wait for the required CI `build` check.
- Use a Conventional Commit PR title, such as `feat: add a language filter`,
  `fix: reconnect chat`, or `chore: update dependencies`. Squash merge uses that title.
- Use `feat!:`/`fix!:` and describe migrations for incompatible changes.
- Squash merge; GitHub deletes the merged remote branch. Start a fresh branch
  for the next task rather than reusing a squash-merged branch.
- Solo maintainers can self-review; an independent approval is not required.

## Parallel worktrees

Worktrees are optional separate folders for branches, not a replacement for PRs:

```sh
git fetch origin
git worktree add -b feat/example ../justintv-example origin/main
```

Give each parallel task/agent its own branch and directory. Worktrees share
refs, remotes, tags, and some build resources. Do not alter another task's
branch or force checkout the same branch twice. Configure ignored local SDK/env
files separately; never copy production signing credentials into every worktree.
Inspect and preserve outstanding work before removing a worktree without force.
The existing `feat/channel-search` worktree predates the README history rewrite;
do not merge its old history into `main` without reviewing it.

## Versions and releases

`version.txt` is the sole application-version input. Gradle derives Android's
upgrade code as `major * 10000 + minor * 100 + patch` (`0.1.0` → `100`).
Components must be canonical numbers in 0..99; `0.0.0` is invalid.

- Fixes: `0.1.1`; features/milestones or incompatible development changes: `0.2.0`.
- `1.0.0` signals a stable user-facing compatibility contract.
- One increasing stable stream only. Prereleases and parallel maintenance
  require a new Android version-code policy; do not strip suffixes or reuse codes.
- release-please maintains a PR updating `version.txt`, `CHANGELOG.md`, and its
  bookkeeping manifest. Review its proposed version, notes, and migrations.
- If bot-created PR checks await approval, approve their CI run on GitHub.
- Merging a release PR does **not** publish. After its CI succeeds and a signed
  APK passes a physical-device smoke test, a maintainer pushes the matching tag:

```sh
git switch main
git pull --ff-only
version="$(cat version.txt)"
git tag -a "v$version" -m "JustinTV $version"
git push origin "v$version"
```

The release workflow repeats checks, verifies the signing certificate and APK
metadata, and publishes the APK/checksum with that version's changelog notes.
It rejects duplicates (including drafts), mismatches, and downgrades. GitHub
tag rules prohibit updating or deleting existing `v*` tags.

Signing is configured through repository secrets: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
`ANDROID_SIGNING_CERT_SHA256`, and `TWITCH_CLIENT_ID`. Keep a secure off-machine
backup of the signing key/password; GitHub secrets cannot be read back.
Local signing can use gitignored `keystore.properties` (storeFile, storePassword,
keyAlias, keyPassword). A debug install normally cannot be upgraded by the
release key; uninstalling it loses local data.

Never rebuild/replace a shipped version. If publication leaves an unpublished
draft, review it and complete publication with its verified artifacts, or remove
only that unpublished draft before retrying. Do not alter published APKs or tags.
