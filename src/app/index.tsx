import { Column, Host, Text, useMaterialColors } from '@expo/ui/jetpack-compose';
import { background, fillMaxSize, paddingAll } from '@expo/ui/jetpack-compose/modifiers';

export default function Home() {
  const colors = useMaterialColors();

  return (
    <Host style={{ flex: 1 }}>
      <Column modifiers={[fillMaxSize(), background(colors.background), paddingAll(24)]}>
        <Text color={colors.onBackground}>justintv</Text>
      </Column>
    </Host>
  );
}
