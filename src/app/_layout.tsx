import { Stack } from 'expo-router';

import { applyActiveProxy } from '@/core/proxy/store';
import { SessionProvider } from '@/components/session-provider';

applyActiveProxy();

export default function RootLayout() {
  return (
    <SessionProvider>
      <Stack>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="watch/[login]" options={{ title: '' }} />
        <Stack.Screen name="proxies" options={{ title: 'Proxies' }} />
        <Stack.Screen name="account" options={{ title: 'Account' }} />
      </Stack>
    </SessionProvider>
  );
}
