import {
  createContext,
  use,
  useEffect,
  useState,
  useSyncExternalStore,
  type PropsWithChildren,
} from 'react';
import * as SecureStore from 'expo-secure-store';
import { AppState } from 'react-native';
import { ENV } from 'varlock/env';

import { TwitchSession } from '@/core/twitch/session';

const KEY = 'twitch.session';

const SessionContext = createContext<TwitchSession | null>(null);

export function SessionProvider({ children }: PropsWithChildren) {
  const [session] = useState(
    () =>
      new TwitchSession(ENV.TWITCH_CLIENT_ID ?? '', {
        read: () => SecureStore.getItemAsync(KEY),
        write: (value) => SecureStore.setItemAsync(KEY, value),
        clear: () => SecureStore.deleteItemAsync(KEY),
      }),
  );

  useEffect(() => {
    void session.restore();

    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void session.validate();
    });

    const timer = setInterval(
      () => {
        if (AppState.currentState === 'active') void session.validate();
      },
      60 * 60 * 1000,
    );

    return () => {
      subscription.remove();
      clearInterval(timer);
      session.dispose();
    };
  }, [session]);

  return <SessionContext value={session}>{children}</SessionContext>;
}

export function useSession() {
  const session = use(SessionContext);

  if (!session) throw new Error('useSession requires SessionProvider');

  const state = useSyncExternalStore(session.subscribe, session.getSnapshot);

  return { session, ...state };
}
