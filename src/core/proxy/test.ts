import { Effect, Predicate } from 'effect';

import TwitchNet from '../../../modules/twitch-net/src/TwitchNetModule';
import type { ProxyAddress } from './parse';

export type ProxyTestResult = { ok: true; ms: number } | { ok: false; error: string };

/** Reaches Twitch's usher host through the proxy. Any HTTP response counts as reachable. */
export const testProxy = (proxy: ProxyAddress) =>
  Effect.tryPromise({
    try: () => TwitchNet.testProxy(proxy.host, proxy.port, proxy.type),
    catch: (e) => (e instanceof Error ? e.message : String(e)),
  }).pipe(
    Effect.timeout('8 seconds'),
    Effect.map((r): ProxyTestResult => ({ ok: true, ms: r.ms })),
    Effect.catch((e) =>
      Effect.succeed<ProxyTestResult>({
        ok: false,
        error: Predicate.isString(e) ? e : 'Timed out',
      }),
    ),
  );
