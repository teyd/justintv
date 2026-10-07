import { Effect, Schema } from 'effect';
import Storage from 'expo-sqlite/kv-store';

import TwitchNet from '../../../modules/twitch-net/src/TwitchNetModule';
import { type Proxy, ProxyList } from './parse';

const LIST_KEY = 'proxies';
const ACTIVE_KEY = 'proxies.active';

const decodeList = Schema.decodeUnknownEffect(Schema.fromJsonString(ProxyList));

export const loadProxies = (): Proxy[] => {
  const raw = Storage.getItemSync(LIST_KEY);
  if (raw == null) return [];
  return Effect.runSync(
    decodeList(raw).pipe(Effect.orElseSucceed(() => [] as readonly Proxy[])),
  ).slice();
};

export const saveProxies = (list: readonly Proxy[]) => {
  Storage.setItemSync(LIST_KEY, JSON.stringify(list));
};

export const getActiveProxyId = () => Storage.getItemSync(ACTIVE_KEY);

export const setActiveProxyId = (id: string | null) => {
  if (id == null) Storage.removeItemSync(ACTIVE_KEY);
  else Storage.setItemSync(ACTIVE_KEY, id);
  applyActiveProxy();
};

/** Pushes the active proxy to the native layer. Call once on startup and after changes. */
export const applyActiveProxy = () => {
  const id = getActiveProxyId();
  const proxy = loadProxies().find((p) => p.id === id);
  if (proxy) TwitchNet.setProxy(proxy.host, proxy.port, proxy.type);
  else TwitchNet.setProxy(null, 0, 'http');
};
