import { Schema } from 'effect';

export const ProxyType = Schema.Literals(['http', 'socks']);

export const Proxy = Schema.Struct({
  id: Schema.String,
  name: Schema.String,
  host: Schema.String.check(Schema.isNonEmpty()),
  port: Schema.Number.check(Schema.isInt(), Schema.isBetween({ minimum: 1, maximum: 65535 })),
  type: ProxyType,
});
export type Proxy = typeof Proxy.Type;

export const ProxyList = Schema.Array(Proxy);

export type ProxyAddress = Pick<Proxy, 'host' | 'port' | 'type'>;

const ADDRESS = /^(?:(https?|socks[45]?h?):\/\/)?([^\s/:]+):(\d{1,5})\/?$/i;

/** Accepts `host:port`, `http://host:port` and `socks5://host:port`. */
export function parseProxyAddress(input: string): ProxyAddress | null {
  const match = ADDRESS.exec(input.trim());
  if (!match) return null;
  const [, scheme, host, rawPort] = match;
  const port = Number(rawPort);
  if (port < 1 || port > 65535) return null;
  return { host, port, type: scheme?.toLowerCase().startsWith('socks') ? 'socks' : 'http' };
}
