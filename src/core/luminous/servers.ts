import { Effect, Schema } from 'effect';
import Storage from 'expo-sqlite/kv-store';

export type LuminousServer = { id: string; name: string; base: string };

/** Public luminous-ttv playlist proxies. They fetch the Twitch playlist from a region without ads. */
export const LUMINOUS_SERVERS: readonly LuminousServer[] = [
  { id: 'eu', name: 'Europe', base: 'https://eu.luminous.dev' },
  { id: 'eu2', name: 'Europe 2', base: 'https://eu2.luminous.dev' },
  { id: 'as', name: 'Asia', base: 'https://as.luminous.dev' },
];

const DISABLED_KEY = 'luminous.disabled';

const decodeIds = Schema.decodeUnknownEffect(Schema.fromJsonString(Schema.Array(Schema.String)));

export const loadDisabledLuminous = (): string[] => {
  const raw = Storage.getItemSync(DISABLED_KEY);
  if (raw == null) return [];
  return Effect.runSync(
    decodeIds(raw).pipe(Effect.orElseSucceed(() => [] as readonly string[])),
  ).slice();
};

export const setLuminousEnabled = (id: string, enabled: boolean) => {
  const rest = loadDisabledLuminous().filter((x) => x !== id);
  Storage.setItemSync(DISABLED_KEY, JSON.stringify(enabled ? rest : [...rest, id]));
};

export const enabledLuminousServers = () => {
  const disabled = loadDisabledLuminous();
  return LUMINOUS_SERVERS.filter((s) => !disabled.includes(s.id));
};

export const luminousPlaylistUrl = (server: LuminousServer, login: string) => {
  const params = new URLSearchParams({
    allow_source: 'true',
    allow_audio_only: 'true',
    fast_bread: 'true',
  });
  return `${server.base}/live/${encodeURIComponent(login.toLowerCase())}?${params}`;
};
