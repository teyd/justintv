import { Button, Column, Host } from '@expo/ui';
import * as WebBrowser from 'expo-web-browser';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useSession } from '@/components/session-provider';

export default function Account() {
  const { session, loading, busy, login, error, activation } = useSession();
  const [browserError, setBrowserError] = useState<string | null>(null);
  const dark = useColorScheme() !== 'light';
  const insets = useSafeAreaInsets();
  const text = dark ? '#efeff1' : '#0e0e10';
  const muted = dark ? '#adadb8' : '#53535f';

  useEffect(() => () => session.cancel(), [session]);

  const openBrowser = async (url: string) => {
    setBrowserError(null);

    try {
      await WebBrowser.openBrowserAsync(url);
    } catch {
      setBrowserError(
        'Could not open your browser. Visit twitch.tv/activate and enter the code below.',
      );
    }
  };

  const signIn = async () => {
    setBrowserError(null);
    const url = await session.signIn();

    if (url) await openBrowser(url);
  };

  return (
    <ScrollView
      style={{ backgroundColor: dark ? '#0e0e10' : '#f7f7f8' }}
      contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
    >
      <Text style={[styles.title, { color: text }]}>
        {login ? `Hi, ${login}` : 'Your Twitch, connected'}
      </Text>
      <Text style={[styles.description, { color: muted }]}>
        {login
          ? 'Your Twitch account is connected. Browsing and playback still work independently of your login.'
          : 'Connect securely through Twitch. We never ask for your password, and you can keep watching without signing in.'}
      </Text>

      {loading ? (
        <View style={styles.status}>
          <ActivityIndicator color="#9147ff" />
          <Text style={{ color: muted }}>Restoring your session…</Text>
        </View>
      ) : (
        <>
          {activation ? (
            <View style={[styles.card, { backgroundColor: dark ? '#18181b' : '#fff' }]}>
              <Text style={{ color: text }}>Your activation code</Text>
              <Text selectable style={[styles.code, { color: text }]}>
                {activation.userCode}
              </Text>
              <Text style={{ color: muted }}>
                Approve on Twitch, then close the browser to return here. You can also enter this
                code at twitch.tv/activate on another device.
              </Text>
              <View style={styles.status}>
                <ActivityIndicator color="#9147ff" />
                <Text style={{ color: muted }}>Waiting for approval…</Text>
              </View>
            </View>
          ) : null}

          {error ? (
            <Text accessibilityRole="alert" style={styles.error}>
              {error}
            </Text>
          ) : null}
          {browserError && activation ? (
            <Text accessibilityRole="alert" style={styles.error}>
              {browserError}
            </Text>
          ) : null}

          <Host matchContents>
            <Column spacing={12}>
              {login ? (
                <>
                  {error ? (
                    <Button
                      label="Retry connection"
                      disabled={busy}
                      onPress={() => void session.validate()}
                    />
                  ) : null}
                  <Button
                    label={busy ? 'Signing out…' : 'Sign out'}
                    variant="outlined"
                    disabled={busy}
                    onPress={() => void session.signOut()}
                  />
                </>
              ) : activation ? (
                <>
                  <Button
                    label="Open Twitch again"
                    onPress={() => void openBrowser(activation.url)}
                  />
                  <Button label="Cancel sign-in" variant="text" onPress={session.cancel} />
                </>
              ) : (
                <>
                  <Button
                    label={busy ? 'Connecting…' : 'Continue with Twitch'}
                    disabled={busy}
                    onPress={() => void signIn()}
                  />
                  {busy ? (
                    <Button label="Cancel sign-in" variant="text" onPress={session.cancel} />
                  ) : null}
                  {error ? (
                    <Button
                      label="Clear saved session"
                      variant="text"
                      disabled={busy}
                      onPress={() => void session.signOut()}
                    />
                  ) : null}
                </>
              )}
            </Column>
          </Host>
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 24, gap: 24 },
  title: { fontSize: 28, fontWeight: '800' },
  description: { fontSize: 16, lineHeight: 24 },
  card: { padding: 20, borderRadius: 16, gap: 16 },
  code: { fontSize: 32, fontWeight: '800', letterSpacing: 4 },
  status: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  error: { color: '#f4364c', lineHeight: 22 },
});
