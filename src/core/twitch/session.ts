import { Option, Schema } from 'effect';

import {
  AuthError,
  pollDeviceLogin,
  refreshTokens,
  revokeToken,
  startDeviceLogin,
  Tokens,
  validateToken,
} from './auth';

const Session = Schema.Struct({
  clientId: Schema.String,
  tokens: Tokens,
  login: Schema.String,
});

type Session = typeof Session.Type;

export interface SessionStorage {
  read: () => Promise<string | null>;
  write: (value: string) => Promise<void>;
  clear: () => Promise<void>;
}

export interface AuthSnapshot {
  loading: boolean;
  busy: boolean;
  login: string | null;
  error: string | null;
  activation: { userCode: string; url: string; expiresAt: number } | null;
}

/** Owns token rotation and persistence; tokens never enter the UI or playback clients. */
export class TwitchSession {
  private session: Session | null = null;
  private snapshot: AuthSnapshot = {
    loading: true,
    busy: false,
    login: null,
    error: null,
    activation: null,
  };
  private listeners = new Set<() => void>();
  private controller = new AbortController();
  private generation = 0;
  private writes: Promise<void> = Promise.resolve();
  private validation: Promise<void> | null = null;
  private signingIn = false;

  constructor(
    private readonly clientId: string,
    private readonly storage: SessionStorage,
  ) {}

  getSnapshot = () => this.snapshot;

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);

    return () => {
      this.listeners.delete(listener);
    };
  };

  private publish(update: Partial<AuthSnapshot>) {
    this.snapshot = { ...this.snapshot, ...update };

    for (const listener of this.listeners) listener();
  }

  private persist(value: Session | null) {
    const next = this.writes
      .catch(() => {})
      .then(() => (value ? this.storage.write(JSON.stringify(value)) : this.storage.clear()));

    this.writes = next;

    return next;
  }

  private resetOperation() {
    this.controller.abort();
    this.controller = new AbortController();
    this.generation += 1;
    this.validation = null;
    this.signingIn = false;
  }

  private report(error: Error) {
    this.publish({
      error:
        error instanceof AuthError
          ? error.reason
          : 'Could not save your session securely. Please retry.',
    });
  }

  async restore() {
    this.resetOperation();
    const generation = this.generation;
    this.publish({ loading: true });

    try {
      const raw = await this.storage.read();

      if (generation !== this.generation) return;

      if (raw) {
        const decoded = Schema.decodeUnknownOption(Schema.fromJsonString(Session))(raw);

        // Decode persisted data rather than trusting a previous app version's storage.
        if (Option.isSome(decoded) && decoded.value.clientId === this.clientId) {
          this.session = decoded.value;
          this.publish({ login: decoded.value.login });
        } else {
          await this.persist(null);
        }
      }

      await this.validate();
    } catch (error) {
      if (generation === this.generation)
        this.report(error instanceof Error ? error : new Error('Storage unavailable'));
    } finally {
      if (generation === this.generation) this.publish({ loading: false });
    }
  }

  validate(): Promise<void> {
    if (this.validation) return this.validation;

    if (!this.session || this.snapshot.busy) return Promise.resolve();

    const pending = this.checkSession();
    this.validation = pending;
    void pending.finally(() => {
      if (this.validation === pending) this.validation = null;
    });

    return pending;
  }

  private async checkSession() {
    const generation = this.generation;
    let session = this.session;

    if (!session) return;

    try {
      let identity;

      try {
        identity = await validateToken(
          this.clientId,
          session.tokens.access_token,
          this.controller.signal,
        );
      } catch (error) {
        if (!(error instanceof AuthError) || error.kind !== 'invalid') throw error;

        const tokens = await refreshTokens(
          this.clientId,
          session.tokens.refresh_token,
          this.controller.signal,
        );

        if (generation !== this.generation) return;

        session = { ...session, tokens };
        this.session = session;
        // Public-client refresh tokens are single-use. Save their replacement immediately.
        await this.persist(session);

        if (generation !== this.generation) return;

        identity = await validateToken(this.clientId, tokens.access_token, this.controller.signal);
      }

      if (generation !== this.generation) return;

      session = { ...session, login: identity.login };
      await this.persist(session);

      if (generation !== this.generation) return;

      this.session = session;
      this.publish({ login: identity.login, error: null });
    } catch (error) {
      if (generation !== this.generation) return;

      if (error instanceof AuthError && error.kind === 'invalid') {
        this.session = null;
        this.publish({ login: null, error: 'Your Twitch session ended. Please sign in again.' });
        await this.persist(null).catch(() =>
          this.publish({ error: 'Could not remove your saved session. Please retry sign-out.' }),
        );
      } else {
        this.report(error instanceof Error ? error : new Error('Session unavailable'));
      }
    }
  }

  async signIn(): Promise<string | null> {
    if (this.snapshot.busy || this.snapshot.loading || this.session) return null;

    if (!this.clientId) {
      this.publish({
        error: 'Set TWITCH_CLIENT_ID in .env.local and restart Expo before signing in.',
      });

      return null;
    }

    this.resetOperation();
    const generation = this.generation;
    this.signingIn = true;
    this.publish({ busy: true, error: null, activation: null });

    try {
      const code = await startDeviceLogin(this.clientId, this.controller.signal);

      if (generation !== this.generation) return null;

      this.publish({
        activation: {
          userCode: code.user_code,
          url: code.verification_uri,
          expiresAt: code.expiresAt,
        },
      });
      void this.completeLogin(code, generation);

      return code.verification_uri;
    } catch (error) {
      if (generation === this.generation) {
        this.report(error instanceof Error ? error : new Error('Sign-in failed'));
        this.signingIn = false;
        this.publish({ busy: false });
      }

      return null;
    }
  }

  private async completeLogin(code: Parameters<typeof pollDeviceLogin>[1], generation: number) {
    try {
      const tokens = await pollDeviceLogin(this.clientId, code, this.controller.signal, (error) => {
        if (generation === this.generation) this.publish({ error });
      });

      const identity = await validateToken(
        this.clientId,
        tokens.access_token,
        this.controller.signal,
      );

      if (generation !== this.generation) return;

      const session = { clientId: this.clientId, tokens, login: identity.login };
      await this.persist(session);

      if (generation !== this.generation) return;

      this.session = session;
      this.signingIn = false;
      this.publish({ login: identity.login, busy: false, activation: null, error: null });
    } catch (error) {
      if (generation === this.generation) {
        this.report(error instanceof Error ? error : new Error('Sign-in failed'));
        this.signingIn = false;
        this.publish({ busy: false, activation: null });
      }
    }
  }

  cancel = () => {
    if (!this.signingIn) return;

    this.resetOperation();
    this.publish({ busy: false, activation: null, error: null });
    // A secure write may already be in flight; queue deletion behind it.
    void this.persist(null).catch(() =>
      this.publish({ error: 'Could not clear the cancelled session. Please retry sign-out.' }),
    );
  };

  async signOut() {
    const token = this.session?.tokens.access_token;
    this.resetOperation();
    this.session = null;
    this.publish({ login: null, busy: true, activation: null, error: null });

    try {
      await this.persist(null);
    } catch {
      this.publish({ error: 'Could not remove your saved session. Please retry sign-out.' });
    } finally {
      this.publish({ busy: false });
    }

    if (token) void revokeToken(this.clientId, token).catch(() => {});
  }

  dispose = () => {
    this.cancel();
    this.resetOperation();
  };
}
