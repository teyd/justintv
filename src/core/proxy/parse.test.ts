import { describe, expect, test } from 'vite-plus/test';

import { parseProxyAddress } from './parse';

describe('parseProxyAddress', () => {
  test('defaults to http', () => {
    expect(parseProxyAddress('proxy.example.com:8080')).toEqual({
      host: 'proxy.example.com',
      port: 8080,
      type: 'http',
    });
  });

  test('reads the scheme', () => {
    expect(parseProxyAddress('http://1.2.3.4:3128')?.type).toBe('http');
    expect(parseProxyAddress('socks5://1.2.3.4:1080')?.type).toBe('socks');
  });

  test('rejects invalid input', () => {
    expect(parseProxyAddress('')).toBeNull();
    expect(parseProxyAddress('no-port')).toBeNull();
    expect(parseProxyAddress('host:0')).toBeNull();
    expect(parseProxyAddress('host:70000')).toBeNull();
  });
});
