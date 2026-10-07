import { Data, Effect } from 'effect';

import {
  enabledLuminousServers,
  luminousPlaylistUrl,
  type LuminousServer,
} from '../luminous/servers';
import { fetchPlaybackUrl } from './gql';

class LuminousError extends Data.TaggedError('LuminousError')<{ server: string; reason: string }> {}

/** Succeeds only if the server returns a real playlist, so offline servers and channels both lose the race. */
const probe = (server: LuminousServer, login: string) => {
  const url = luminousPlaylistUrl(server, login);
  return Effect.tryPromise({
    try: async (signal) => {
      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      if (!(await res.text()).startsWith('#EXTM3U')) throw new Error('Not a playlist');
      return url;
    },
    catch: (e) =>
      new LuminousError({ server: server.id, reason: e instanceof Error ? e.message : String(e) }),
  }).pipe(Effect.timeout('6 seconds'));
};

/** Playlist URL from the first enabled luminous server that answers. */
export const fetchLuminousPlaylistUrl = (login: string) => {
  const servers = enabledLuminousServers();
  return servers.length === 0
    ? Effect.fail(new LuminousError({ server: '-', reason: 'No servers enabled' }))
    : Effect.raceAll(servers.map((s) => probe(s, login)));
};

/** Luminous first, then Twitch directly (through the selected proxy, if any). */
export const resolvePlaybackUrl = (login: string) =>
  fetchLuminousPlaylistUrl(login).pipe(Effect.catch(() => fetchPlaybackUrl(login)));
