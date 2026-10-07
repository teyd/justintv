import { Stack } from 'expo-router';

import { applyActiveProxy } from '@/core/proxy/store';

applyActiveProxy();

export default function RootLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'justintv' }} />
      <Stack.Screen name="proxies" options={{ title: 'Proxies' }} />
    </Stack>
  );
}
