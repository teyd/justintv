import { afterEach, describe, expect, test, vi } from 'vite-plus/test';

import { pollDeviceLogin, refreshTokens, startDeviceLogin, validateToken } from './auth';
import { TwitchSession, type SessionStorage } from './session';

const device = {
  device_code: 'device-secret',
  user_code: 'ABCDEFGH',
  verification_uri: 'https://www.twitch.tv/activate?public=true&device-code=ABCDEFGH',
  expires_in: 1800,
  interval: 5,
};

const tokens = { access_token: 'access', refresh_token: 'refresh', expires_in: 14400 };

const identity = {
  client_id: 'ours',
  user_id: '123',
  login: 'viewer',
  scopes: [],
  expires_in: 14400,
};

const saved = JSON.stringify({ clientId: 'ours', tokens, login: 'viewer' });

const storage = (initial: string | null = null) => {
  let value = initial;

  const adapter: SessionStorage = {
    read: vi.fn(async () => value),
    write: vi.fn(async (next: string) => {
      value = next;
    }),
    clear: vi.fn(async () => {
      value = null;
    }),
  };

  return adapter;
};

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('Twitch device authorization', () => {
  test('uses our client ID with no secret and validates the browser destination', async () => {
    const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(device));
    const code = await startDeviceLogin('ours');

    expect(code.user_code).toBe('ABCDEFGH');
    expect(code.expiresAt).toBeGreaterThan(Date.now());
    expect(request.mock.calls[0]?.[1]?.body).toBe('client_id=ours&scopes=');

    request.mockResolvedValue(
      Response.json({ ...device, verification_uri: 'https://evil.example/activate' }),
    );
    await expect(startDeviceLogin('ours')).rejects.toMatchObject({ kind: 'invalid' });
  });

  test('respects polling interval, pending and permanent slow-down', async () => {
    vi.useFakeTimers();

    const request = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ message: 'authorization_pending' }, { status: 400 }))
      .mockResolvedValueOnce(Response.json({ message: 'slow_down' }, { status: 400 }))
      .mockResolvedValueOnce(Response.json(tokens));

    const code = { ...device, expiresAt: Date.now() + 60_000 };
    const pending = pollDeviceLogin('ours', code, new AbortController().signal);

    await vi.advanceTimersByTimeAsync(4999);
    expect(request).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(request).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(5000);
    expect(request).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(9999);
    expect(request).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(await pending).toEqual(tokens);
  });

  test('stops at the deadline without another token request', async () => {
    vi.useFakeTimers();
    const request = vi.spyOn(globalThis, 'fetch');

    const pending = pollDeviceLogin(
      'ours',
      { ...device, expiresAt: Date.now() + 1000 },
      new AbortController().signal,
    );

    const assertion = expect(pending).rejects.toMatchObject({ kind: 'expired' });
    await vi.advanceTimersByTimeAsync(1000);
    await assertion;
    expect(request).not.toHaveBeenCalled();
  });

  test('cancellation interrupts the polling wait', async () => {
    const request = vi.spyOn(globalThis, 'fetch');
    const controller = new AbortController();

    const pending = pollDeviceLogin(
      'ours',
      { ...device, expiresAt: Date.now() + 60_000 },
      controller.signal,
    );

    const assertion = expect(pending).rejects.toBeDefined();
    controller.abort();
    await assertion;
    expect(request).not.toHaveBeenCalled();
  });

  test('denied approval stops polling', async () => {
    vi.useFakeTimers();

    const request = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(Response.json({ message: 'access_denied' }, { status: 400 }));

    const pending = pollDeviceLogin(
      'ours',
      { ...device, expiresAt: Date.now() + 60_000 },
      new AbortController().signal,
    );

    const assertion = expect(pending).rejects.toMatchObject({ kind: 'denied' });
    await vi.advanceTimersByTimeAsync(5000);
    await assertion;
    expect(request).toHaveBeenCalledTimes(1);
  });

  test('refresh encodes special characters and never sends a client secret', async () => {
    const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(tokens));
    await refreshTokens('ours', 'a+b/&');
    expect(request.mock.calls[0]?.[1]?.body).toBe(
      'client_id=ours&grant_type=refresh_token&refresh_token=a%2Bb%2F%26',
    );
  });

  test('rejects malformed responses and identities for another app', async () => {
    const request = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(Response.json({ ...identity, client_id: 'someone-else' }));

    await expect(validateToken('ours', 'access')).rejects.toMatchObject({ kind: 'invalid' });
    request.mockResolvedValue(Response.json({ access_token: 123 }));
    await expect(refreshTokens('ours', 'refresh')).rejects.toMatchObject({ kind: 'network' });
  });

  test('times out and aborts a stalled transport', async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | null | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_url, options) => {
      signal = options?.signal;

      return new Promise<Response>((_resolve, reject) => {
        signal?.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
      });
    });
    const pending = startDeviceLogin('ours');
    const assertion = expect(pending).rejects.toMatchObject({ kind: 'network' });
    await vi.advanceTimersByTimeAsync(15_000);
    await assertion;
    expect(signal?.aborted).toBe(true);
  });
});

describe('persistent Twitch sessions', () => {
  test('starts as a guest and reports missing configuration without requesting Twitch', async () => {
    const request = vi.spyOn(globalThis, 'fetch');
    const session = new TwitchSession('', storage());
    await session.restore();
    expect(await session.signIn()).toBeNull();
    expect(session.getSnapshot().error).toContain('TWITCH_CLIENT_ID');
    expect(request).not.toHaveBeenCalled();
    session.dispose();
  });

  test('stores a completed login securely without exposing tokens in the snapshot', async () => {
    vi.useFakeTimers();
    const adapter = storage();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(device))
      .mockResolvedValueOnce(Response.json(tokens))
      .mockResolvedValueOnce(Response.json(identity));
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    expect(await session.signIn()).toBe(device.verification_uri);
    expect(session.getSnapshot().activation?.userCode).toBe('ABCDEFGH');
    await vi.advanceTimersByTimeAsync(5000);
    expect(session.getSnapshot()).toMatchObject({ login: 'viewer', busy: false, activation: null });
    expect(JSON.stringify(session.getSnapshot())).not.toContain('refresh_token');
    expect(await adapter.read()).toBe(saved);
    session.dispose();
  });

  test('cancels stale approval and supports another attempt', async () => {
    vi.useFakeTimers();
    const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(device));
    const session = new TwitchSession('ours', storage());
    await session.restore();
    await session.signIn();
    session.cancel();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(request).toHaveBeenCalledTimes(1);
    expect(session.getSnapshot()).toMatchObject({
      login: null,
      busy: false,
      activation: null,
      error: null,
    });
    await session.signIn();
    expect(request).toHaveBeenCalledTimes(2);
    session.dispose();
  });

  test('preserves saved sessions when offline and retries validation on demand', async () => {
    const adapter = storage(saved);
    const request = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'));
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    expect(session.getSnapshot()).toMatchObject({ login: 'viewer', loading: false });
    expect(session.getSnapshot().error).toContain('connection');
    expect(await adapter.read()).toBe(saved);
    request.mockResolvedValue(Response.json(identity));
    await session.validate();
    expect(session.getSnapshot().error).toBeNull();
    session.dispose();
  });

  test('deduplicates validation and saves the single-use refresh token replacement', async () => {
    const adapter = storage(saved);
    const rotated = { ...tokens, access_token: 'new-access', refresh_token: 'new-refresh' };
    const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(identity));
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    request.mockClear();
    request
      .mockResolvedValueOnce(Response.json({ message: 'invalid access token' }, { status: 401 }))
      .mockResolvedValueOnce(Response.json(rotated))
      .mockResolvedValueOnce(Response.json(identity));
    await Promise.all([session.validate(), session.validate(), session.validate()]);
    expect(request).toHaveBeenCalledTimes(3);
    expect(await adapter.read()).toContain('new-refresh');
    expect(session.getSnapshot().error).toBeNull();
    session.dispose();
  });

  test('revoked refresh tokens clear the saved session', async () => {
    const adapter = storage(saved);
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      Response.json({ message: 'invalid access token' }, { status: 401 }),
    );
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    expect(session.getSnapshot().login).toBeNull();
    expect(await adapter.read()).toBeNull();
    session.dispose();
  });

  test('corrupt storage and another client ID are discarded without sending credentials', async () => {
    const request = vi.spyOn(globalThis, 'fetch');

    for (const raw of ['not-json', saved.replace('ours', 'other-app')]) {
      const adapter = storage(raw);
      const session = new TwitchSession('ours', adapter);
      await session.restore();
      expect(await adapter.read()).toBeNull();
      expect(session.getSnapshot().login).toBeNull();
      session.dispose();
    }

    expect(request).not.toHaveBeenCalled();
  });

  test('logout wins over an in-flight validation result', async () => {
    const adapter = storage(saved);
    const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(identity));
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    let complete: ((response: Response) => void) | undefined;
    request.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          complete = resolve;
        }),
    );
    const pending = session.validate();
    await session.signOut();
    complete?.(Response.json(identity));
    await pending;
    expect(session.getSnapshot().login).toBeNull();
    expect(await adapter.read()).toBeNull();
    session.dispose();
  });

  test('cancellation clears a secure write that was already in flight', async () => {
    vi.useFakeTimers();
    const adapter = storage();
    const write = adapter.write;
    let finish: (() => void) | undefined;
    adapter.write = async (value) => {
      await new Promise<void>((resolve) => {
        finish = resolve;
      });
      await write(value);
    };

    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(device))
      .mockResolvedValueOnce(Response.json(tokens))
      .mockResolvedValueOnce(Response.json(identity));
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    await session.signIn();
    await vi.advanceTimersByTimeAsync(5000);
    expect(finish).toBeDefined();
    session.cancel();
    finish?.();
    await vi.advanceTimersByTimeAsync(0);
    expect(await adapter.read()).toBeNull();
    expect(session.getSnapshot().login).toBeNull();
    session.dispose();
  });

  test('retains rotated credentials when the next validation is temporarily offline', async () => {
    const adapter = storage(saved);
    const rotated = { ...tokens, access_token: 'new-access', refresh_token: 'new-refresh' };

    const request = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json(rotated))
      .mockRejectedValueOnce(new Error('offline'));

    const session = new TwitchSession('ours', adapter);
    await session.restore();
    expect(await adapter.read()).toContain('new-refresh');
    expect(session.getSnapshot().login).toBe('viewer');
    request.mockResolvedValueOnce(Response.json(identity));
    await session.validate();
    expect(request).toHaveBeenCalledTimes(4);
    expect(session.getSnapshot().error).toBeNull();
    session.dispose();
  });

  test('does not claim a login if secure storage fails', async () => {
    vi.useFakeTimers();
    const adapter = storage();
    adapter.write = async () => {
      throw new Error('Keystore unavailable');
    };

    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(device))
      .mockResolvedValueOnce(Response.json(tokens))
      .mockResolvedValueOnce(Response.json(identity));
    const session = new TwitchSession('ours', adapter);
    await session.restore();
    await session.signIn();
    await vi.advanceTimersByTimeAsync(5000);
    expect(session.getSnapshot()).toMatchObject({ login: null, busy: false, activation: null });
    expect(session.getSnapshot().error).toContain('securely');
    session.dispose();
  });
});
