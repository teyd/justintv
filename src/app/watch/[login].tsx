import { Effect, Result } from 'effect';
import { useEvent } from 'expo';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useVideoPlayer, VideoView } from 'expo-video';
import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { resolvePlaybackUrl } from '@/core/twitch/playback';

export default function Watch() {
  const { login, name } = useLocalSearchParams<{ login: string; name?: string }>();

  return <WatchChannel key={login} login={login} name={name} />;
}

function WatchChannel({ login, name }: { login: string; name?: string }) {
  const [url, setUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void Effect.runPromise(resolvePlaybackUrl(login).pipe(Effect.result), {
      signal: controller.signal,
    })
      .then((r) => {
        if (controller.signal.aborted) return;

        if (Result.isSuccess(r)) setUrl(r.success);
        else setError(r.failure.reason);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setError(failure instanceof Error ? failure.message : 'Unable to load playback');
        }
      });

    return () => {
      controller.abort();
    };
  }, [login]);

  return (
    <View style={styles.root}>
      <Stack.Screen options={{ title: name ?? login, headerTransparent: false }} />
      {url ? (
        <Player url={url} />
      ) : (
        <View style={styles.video}>
          {error ? <Text style={styles.error}>{error}</Text> : <ActivityIndicator color="#fff" />}
        </View>
      )}
    </View>
  );
}

function Player({ url }: { url: string }) {
  const player = useVideoPlayer({ uri: url, contentType: 'hls' }, (p) => {
    p.play();
  });

  const { status, error: failure } = useEvent(player, 'statusChange', {
    status: player.status,
    oldStatus: player.status,
    error: undefined,
  });

  return (
    <View style={styles.video}>
      <VideoView
        player={player}
        style={StyleSheet.absoluteFill}
        nativeControls
        contentFit="contain"
      />
      {status === 'loading' && <ActivityIndicator color="#fff" />}
      {status === 'error' && (
        <Text style={styles.error}>{failure?.message ?? 'Playback failed'}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000' },
  video: { width: '100%', aspectRatio: 16 / 9, backgroundColor: '#000', justifyContent: 'center' },
  error: { color: '#f4364c', textAlign: 'center', padding: 16 },
});
