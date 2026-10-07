import { Data, Effect, Option, Schema } from 'effect';

const AUTH_URL = 'https://id.twitch.tv/oauth2';

export class AuthError extends Data.TaggedError('AuthError')<{
  reason: string;
  kind: 'network' | 'invalid' | 'pending' | 'slow-down' | 'expired' | 'denied';
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
  scopes: Schema.Array(Schema.String),
  expires_in: Schema.Number,
});

const OAuthFailure = Schema.Struct({ message: Schema.optional(Schema.String) });

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
  const timer = setTimeout(abort, 15_000);
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

      throw failure(Option.isSome(error) ? (error.value.message ?? '') : '', response.status);
    }

    return Schema.decodeUnknownSync(schema)(body);
  } catch (error) {
    if (error instanceof AuthError) throw error;

    throw new AuthError({
      kind: 'network',
      reason: 'Could not reach Twitch. Check your connection and retry.',
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
): Promise<Tokens> => {
  let interval = Math.max(1, code.interval) * 1000;

  while (!signal.aborted && Date.now() < code.expiresAt) {
    await Effect.runPromise(Effect.sleep(Math.min(interval, code.expiresAt - Date.now())), {
      signal,
    });

    if (Date.now() >= code.expiresAt) break;

    try {
      return await request(
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
    } catch (error) {
      if (!(error instanceof AuthError)) throw error;

      if (error.kind === 'slow-down') interval += 5000;
      else if (error.kind !== 'pending') throw error;
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
