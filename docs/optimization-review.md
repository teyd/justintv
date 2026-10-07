# App optimization review

Reviewed against the Expo, data-fetching, native UI, and React Native performance skills using SDK 57 documentation.

## Changes in this pass

- Extract feed requests into `src/hooks/use-live-streams.ts`, leaving layout in the route.
- Stop pagination when the server returns an empty page instead of restarting at the first page.
- Let refresh supersede pagination; abort superseded and unmounted requests and ignore their results.
- Deduplicate streams within a page as well as across pages without mutating existing content.
- Show initial loading explicitly, preserve content on failure, and provide retries for the failed operation. Refresh errors appear above existing content; pagination errors appear below it.
- Pass Effect's cancellation signal to GraphQL fetches and bound stalled requests to 15 seconds.
- Remount the watch content when its channel changes, disposing of the old player and clearing its URL/error state. Abort pending playback resolution on unmount.

## Next priorities

1. **Proxy-test resource lifecycle:** `TwitchNetModule.kt` disconnects its HTTP connection only on success. Move cleanup into `finally`; test failure paths in an Android build. Repeated proxy tests also need a per-proxy in-flight guard in `src/app/proxies.tsx`.
2. **Measure scrolling and playback startup on Android:** profile a release build, recording feed scroll FPS, React commit times, memory after repeated watch/back navigation, and time to first video frame. Compare the same interactions before and after changes. No runtime speedup has been measured in this pass.
3. **Evaluate image caching from measurements:** stream cards use React Native `Image`. Consider SDK-compatible `expo-image` if profiling shows decode/cache pressure; avoid aggressively caching live preview images without a freshness policy.
4. **Analyze the production bundle:** use Expo Atlas before removing dependencies or changing imports. A dependency being listed in `package.json` does not prove it adds runtime bundle weight.
5. **Add device-level regressions:** refresh during pagination, retry after a failed page, reaching the end, navigating back during playback resolution, and switching watch channels. Unit coverage here tests GraphQL decoding, exhaustion, cancellation, timeout, and stream merging—not mounted screens or native playback.

## Keep as-is

- `FlatList` already virtualizes the feed; replacing it with FlashList needs evidence of a scrolling bottleneck.
- React Compiler is already enabled; do not blanket-add `memo`, `useMemo`, or `useCallback` without profiling.
- Settings use native Compose controls. The project explicitly targets Android only; an iOS settings tree and native networking implementation would be a separate feature.
- No new dependencies or SDK upgrade are required for this pass.
