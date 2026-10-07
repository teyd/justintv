import { Button, Column, Host, Text, useMaterialColors } from '@expo/ui/jetpack-compose';
import { background, fillMaxSize, paddingAll } from '@expo/ui/jetpack-compose/modifiers';
import { router } from 'expo-router';

export default function Home() {
  const colors = useMaterialColors();

  return (
    <Host style={{ flex: 1 }}>
      <Column
        modifiers={[fillMaxSize(), background(colors.background), paddingAll(24)]}
        verticalArrangement={{ spacedBy: 16 }}
      >
        <Text color={colors.onBackground}>justintv</Text>
        <Button onClick={() => router.push('/proxies')}>
          <Text>Proxies</Text>
        </Button>
      </Column>
    </Host>
  );
}
