This is an Expo/React Native mobile application. Prioritize mobile-first patterns, performance, and cross-platform compatibility.

## Expo has changed — do not trust your training data

Expo ships breaking changes every SDK release. APIs you remember are likely renamed, moved, or removed. Before writing any code that touches an Expo, EAS, or React Native API:

1. Read the major version of the `expo` package in `package.json`.
2. Fetch the matching versioned docs: `https://docs.expo.dev/versions/v<major>.0.0/`
3. For anything else, fetch https://docs.expo.dev/llms.txt — an index of all Expo docs with corrections to common LLM misconceptions. Follow its links to the specific page you need; never answer from memory.

## Commands

This project uses bun (`bun.lock`), Node 24 and the other tools pinned in `mise.toml`. Use `bun`/`bunx` instead of `npm`/`npx`.

```bash
bun expo install <package>  # ALWAYS use instead of bun add — resolves SDK-compatible versions
bun expo start              # start the dev server
vp check                    # format (oxfmt) + lint (oxlint) + typecheck; `vp check --fix` to autofix
bun run typecheck           # tsc --noEmit (TypeScript 7)
bun expo-doctor             # diagnose dependency and config issues
bun expo install --fix      # fix incompatible package versions
```

`typescript` is intentionally ahead of the Expo SDK's expected version (`expo.install.exclude`). Env vars are declared in `.env.schema` and managed with varlock; use `import { ENV } from 'varlock/env'` rather than `process.env`. Never put secrets in the app bundle.

Run `vp check` before declaring any task done.

## Git workflow

`main` is protected: it only changes through squash-merged pull requests, and CI (`ci`, `pr-title`) must pass. Local hooks also refuse commits and pushes on `main`.

- Never work, commit or push on `main`. Every task gets its own branch in its own git worktree, so parallel agents never share a checkout.
- Create a worktree outside the repo: `git worktree add ../justintv-worktrees/<name> -b <type>/<name> origin/main`. Run `bun install` in it (this also installs the git hooks).
- Name branches `feat/…`, `fix/…`, `chore/…` or `docs/…`, matching the PR type.
- Open a pull request for the branch. The title must follow conventional commits (`feat: add x`). If work depends on an unmerged branch, stack it: base the new branch and its PR on that branch.
- Run `vp check` before pushing. Do not bypass the hooks with `--no-verify`.
- T3 Code starts new threads in a worktree (`defaultThreadEnvMode` in `t3.json`).

## Navigation & Routing

- Use **Expo Router** for all navigation. Routes live in `src/app/` — every file there is a screen, `_layout.tsx` files define navigators. Keep non-route code (components, hooks, utils) outside `src/app/`.
- Import `Link`, `router`, and `useLocalSearchParams` from `expo-router`.
- Docs: https://docs.expo.dev/router/introduction.md

## Building with EAS

Use EAS to build, sign, and submit the app in the cloud (`eas build`, `eas submit`) and to ship over-the-air updates (`eas update`) — no local Xcode or Android Studio required. Run EAS CLI as `bunx eas-cli <command>` in Bun projects, or `npx eas-cli@latest <command>` otherwise; substitute that for bare `eas` in docs examples.
Docs: https://docs.expo.dev/eas/index.md

## Rules

- If `ios/` and `android/` directories do not exist, they are generated (Continuous Native Generation). Never create or edit them by hand — configure native behavior in `app.json` and config plugins.
- Expo Go only includes its bundled native modules. After adding a library with native code, the app needs a development build: `npx expo run:ios|android` locally, or `eas build --profile development`.
- Prefer recommended Expo modules over third-party libraries, and check your available skills before adding dependencies. Docs: https://docs.expo.dev/versions/latest/index.md
