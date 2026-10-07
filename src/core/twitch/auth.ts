import { Data, Effect, Option, Schema } from 'effect';

const AUTH_URL = 'https://id.twitch.tv/oauth2';

export class AuthError extends Data.TaggedError('AuthError')<{
  reason: string;
  kind: 'network' | 'response' | 'invalid' | 'pending' | 'slow-down' | 'expired' | 'denied';
}> {}

export const Tokens = Schema.Struct({
  access_token: Schema.NonEmptyString,
  refresh_token: Schema.NonEmptyString,
  expires_in: Schema.Number,
});

const DeviceCode = Schema.Struct({
  device_code: Schema.NonEmptyString,
  user_code: Schema.NonEmptyString,
  verification_uri: Schema.String,
  expires_in: Schema.Number,
  interval: Schema.Number,
});

const Identity = Schema.Struct({
  client_id: Schema.String,
  user_id: Schema.String,
  login: Schema.String,
});

const OAuthFailure = Schema.Struct({
  message: Schema.optional(Schema.String),
  error: Schema.optional(Schema.String),
});

export type Tokens = typeof Tokens.Type;

export type DeviceCode = typeof DeviceCode.Type & { expiresAt: number };

export type Identity = typeof Identity.Type;

const failure = (message: string, status: number) => {
  switch (message) {
    case 'authorization_pending':
      return new AuthError({ kind: 'pending', reason: 'Waiting for Twitch approval.' });
    case 'slow_down':
      return new AuthError({ kind: 'slow-down', reason: 'Waiting for Twitch approval.' });
    case 'access_denied':
      return new AuthError({
        kind: 'denied',
        reason: 'Twitch sign-in was declined. You can try again.',
      });
    case 'expired_token':
    case 'invalid device code':
      return new AuthError({
        kind: 'expired',
        reason: 'This activation code expired. Start again.',
      });
    default:
      return new AuthError({
        kind: status === 400 || status === 401 || status === 403 ? 'invalid' : 'network',
        reason:
          status === 400 || status === 401 || status === 403
            ? 'Twitch rejected the request. Check that your client ID belongs to a Public Twitch app.'
            : 'Twitch is unavailable. Check your connection and retry.',
      });
  }
};

const request = async <A, I>(
  path: string,
  schema: Schema.Codec<A, I>,
  options: RequestInit,
  signal?: AbortSignal,
): Promise<A> => {
  const controller = new AbortController();
  const abort = () => controller.abort();
  let timedOut = false;

  const timer = setTimeout(() => {
    timedOut = true;
    abort();
  }, 15_000);

  signal?.addEventListener('abort', abort, { once: true });

  if (signal?.aborted) abort();

  try {
    const response = await fetch(`${AUTH_URL}/${path}`, {
      ...options,
      signal: controller.signal,
    });

    const body: unknown = await response.json().catch(() => null);

    if (!response.ok) {
      const error = Schema.decodeUnknownOption(OAuthFailure)(body);

      const message = Option.isSome(error) ? (error.value.message ?? error.value.error ?? '') : '';

      const rejected = failure(message, response.status);

      throw new AuthError({
        kind: rejected.kind,
        reason: `${rejected.reason} (Twitch /${path}, HTTP ${response.status})`,
      });
    }

    const decoded = Schema.decodeUnknownOption(schema)(body);

    if (Option.isNone(decoded)) {
      throw new AuthError({
        kind: 'response',
        reason: `Twitch /${path} returned an unexpected response (HTTP ${response.status}). This is not a connection error. Please report this message.`,
      });
    }

    return decoded.value;
  } catch (error) {
    if (error instanceof AuthError) throw error;

    throw new AuthError({
      kind: 'network',
      reason: timedOut
        ? `Twitch /${path} timed out. Check the emulator's connection and retry.`
        : `Could not reach id.twitch.tv /${path}. Check your connection and retry.`,
    });
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
};

const form = (body: URLSearchParams): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: body.toString(),
});

export const startDeviceLogin = async (
  clientId: string,
  signal?: AbortSignal,
): Promise<DeviceCode> => {
  // Profile identity needs no extra scopes. Request more only when adding account features.
  const code = await request(
    'device',
    DeviceCode,
    form(new URLSearchParams({ client_id: clientId, scopes: '' })),
    signal,
  );

  const url = new URL(code.verification_uri);

  if (
    url.origin !== 'https://www.twitch.tv' ||
    url.username !== '' ||
    url.password !== '' ||
    url.pathname !== '/activate'
  ) {
    throw new AuthError({
      kind: 'invalid',
      reason: 'Twitch returned an unexpected activation URL.',
    });
  }

  if (code.expires_in <= 0 || code.interval <= 0) {
    throw new AuthError({ kind: 'invalid', reason: 'Twitch returned an invalid activation code.' });
  }

  return { ...code, expiresAt: Date.now() + code.expires_in * 1000 };
};

export const pollDeviceLogin = async (
  clientId: string,
  code: DeviceCode,
  signal: AbortSignal,
  onConnectionChange?: (message: string | null) => void,
): Promise<Tokens> => {
  let interval = Math.max(1, code.interval) * 1000;
  let networkFailures = 0;

  while (!signal.aborted && Date.now() < code.expiresAt) {
    await Effect.runPromise(Effect.sleep(Math.min(interval, code.expiresAt - Date.now())), {
      signal,
    });

    if (Date.now() >= code.expiresAt) break;

    try {
      const tokens = await request(
        'token',
        Tokens,
        form(
          new URLSearchParams({
            client_id: clientId,
            device_code: code.device_code,
            grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
            scopes: '',
          }),
        ),
        signal,
      );

      onConnectionChange?.(null);

      return tokens;
    } catch (error) {
      if (!(error instanceof AuthError)) throw error;

      if (signal.aborted) throw error;

      if (error.kind === 'network') {
        networkFailures += 1;

        if (networkFailures >= 5) throw error;

        interval = Math.max(interval, Math.min(30_000, 5000 * 2 ** networkFailures));
        onConnectionChange?.(`${error.reason} Retrying automatically…`);
      } else {
        networkFailures = 0;
        onConnectionChange?.(null);

        if (error.kind === 'slow-down') interval += 5000;
        else if (error.kind !== 'pending') throw error;
      }
    }
  }

  throw new AuthError({ kind: 'expired', reason: 'This activation code expired. Start again.' });
};

export const validateToken = async (clientId: string, token: string, signal?: AbortSignal) => {
  const identity = await request(
    'validate',
    Identity,
    { headers: { Authorization: `OAuth ${token}` } },
    signal,
  );

  if (identity.client_id !== clientId || !identity.user_id || !identity.login) {
    throw new AuthError({
      kind: 'invalid',
      reason: 'This session does not belong to this Twitch app. Sign in again.',
    });
  }

  return identity;
};

export const refreshTokens = (clientId: string, token: string, signal?: AbortSignal) =>
  request(
    'token',
    Tokens,
    form(
      new URLSearchParams({
        client_id: clientId,
        grant_type: 'refresh_token',
        refresh_token: token,
      }),
    ),
    signal,
  );

export const revokeToken = async (clientId: string, token: string) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15_000);

  try {
    await fetch(`${AUTH_URL}/revoke`, {
      ...form(new URLSearchParams({ client_id: clientId, token })),
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timer);
  }
};
