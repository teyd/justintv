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
bun run dev                 # Expo dev server for this worktree (stable port + adb reverse)
bun run dev:android         # build/install/launch on an attached device, same port
bun run dev:stop            # stop this worktree's Expo/Metro processes
vp check                    # format (oxfmt) + lint (oxlint) + typecheck; `vp check --fix` to autofix
bun run typecheck           # tsc --noEmit (TypeScript 7)
bun expo-doctor             # diagnose dependency and config issues
bun expo install --fix      # fix incompatible package versions
```

`typescript` is intentionally ahead of the Expo SDK's expected version (`expo.install.exclude`). Env vars are declared in `.env.schema` and managed with varlock; use `import { ENV } from 'varlock/env'` rather than `process.env`. Never put secrets in the app bundle.

Each worktree gets its own dev server: `bun run dev` assigns a stable per-worktree port, stops any stale servers for that checkout, and remaps `adb reverse tcp:8081` per device so the installed debug build always talks to the worktree that started it. Never run bare `expo start` in a worktree — on a busy port it prompts and exits in non-interactive shells, and the debug build dials `localhost:8081`, which can belong to another worktree. Avoid `--clear`; it wipes the shared `/tmp/metro-cache` for every worktree.

Run `vp check` before declaring any task done.

## Git workflow

`main` is protected: it only changes through squash-merged pull requests, and CI (`ci`, `pr-title`) must pass. Local hooks also refuse commits and pushes on `main`.

- Never work, commit or push on `main`. Every task gets its own branch in its own git worktree, so parallel agents never share a checkout.
- Create a worktree outside the repo: `git worktree add ../justintv-worktrees/<name> -b <type>/<name> origin/main`. Run `bun install && bun scripts/worktree-setup.ts` in it (this installs the git hooks and copies `.env.local` from the primary checkout).
- Name branches `feat/…`, `fix/…`, `chore/…` or `docs/…`, matching the PR type.
- Open a pull request for the branch. The title must follow conventional commits (`feat: add x`). If work depends on an unmerged branch, stack it: base the new branch and its PR on that branch.
- Run `vp check` before pushing. Do not bypass the hooks with `--no-verify`.
- T3 Code starts new threads in a worktree (`defaultThreadEnvMode` in `t3.json`).

## Lint (anti-slop)

The [anti-slop](https://github.com/dmmulroy/anti-slop) Oxlint ruleset is vendored at `tools/oxlint/anti-slop/` and registered in `vite.config.ts`. It rejects low-evidence patterns: unparsed `unknown`, runtime `typeof`, unsafe dictionary types, manual `_tag` comparisons, module mocks, and unexplained type assertions. The Effect rule group is enabled because the app depends on Effect.

- All rules are errors: run `vp check` (or `vp check --fix`) before declaring work done. `require-readable-spacing` autofixes; type assertions need a `// SAFETY: <invariant>` comment.
- The vendored directory imports from `vite-plus/lint/plugins`; do not add `@oxlint/plugins` as a dependency. It stays out of lint, fmt and tsc (see `vite.config.ts` and `tsconfig.json`).
- To update the rules: copy `src/` from the upstream repo over `tools/oxlint/anti-slop/`, re-apply the `vite-plus/lint/plugins` import rewrite, review new rules in `vite.config.ts`, then run `vp check --fix`.

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
