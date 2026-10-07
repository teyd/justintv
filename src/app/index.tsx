import { Effect } from 'effect';
import { Link, router } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { fetchLiveStreams, formatViewers, type LiveStream } from '@/core/twitch/gql';

const palette = {
  dark: { bg: '#0e0e10', card: '#18181b', text: '#efeff1', muted: '#adadb8', accent: '#9147ff' },
  light: { bg: '#f7f7f8', card: '#ffffff', text: '#0e0e10', muted: '#53535f', accent: '#772ce8' },
};

export default function Live() {
  const c = palette[useColorScheme() === 'light' ? 'light' : 'dark'];
  const insets = useSafeAreaInsets();
  const [streams, setStreams] = useState<LiveStream[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const cursor = useRef<string | null>(null);
  const busy = useRef(false);

  const load = useCallback(async (reset: boolean) => {
    if (busy.current) return;
    busy.current = true;
    if (reset) setRefreshing(true);
    else setLoadingMore(true);
    const result = await Effect.runPromise(
      fetchLiveStreams(reset ? null : cursor.current).pipe(Effect.result),
    );
    if (result._tag === 'Success') {
      const page = result.success;
      cursor.current = page.cursor;
      setError(null);
      setStreams((prev) => {
        const base = reset ? [] : prev;
        const seen = new Set(base.map((s) => s.id));
        return [...base, ...page.streams.filter((s) => !seen.has(s.id))];
      });
    } else {
      setError(result.failure.reason);
    }
    busy.current = false;
    setRefreshing(false);
    setLoadingMore(false);
  }, []);

  useEffect(() => {
    void load(true);
  }, [load]);

  return (
    <View style={[styles.root, { backgroundColor: c.bg }]}>
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <View>
          <Text style={[styles.title, { color: c.text }]}>Live now</Text>
          <View style={styles.liveRow}>
            <View style={styles.dot} />
            <Text style={{ color: c.muted }}>Top streams on Twitch</Text>
          </View>
        </View>
        <Link href="/proxies" asChild>
          <Pressable style={StyleSheet.flatten([styles.chip, { backgroundColor: c.card }])}>
            <Text style={{ color: c.text, fontWeight: '600' }}>Proxies</Text>
          </Pressable>
        </Link>
      </View>

      <FlatList
        data={streams}
        keyExtractor={(s) => s.id}
        contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 16 }]}
        refreshing={refreshing}
        onRefresh={() => load(true)}
        onEndReachedThreshold={0.6}
        onEndReached={() => streams.length > 0 && load(false)}
        ListEmptyComponent={
          refreshing ? null : (
            <Text style={[styles.empty, { color: error ? '#f4364c' : c.muted }]}>
              {error ?? 'No live streams'}
            </Text>
          )
        }
        ListFooterComponent={loadingMore ? <ActivityIndicator color={c.accent} /> : null}
        renderItem={({ item }) => (
          <StreamCard
            stream={item}
            colors={c}
            onPress={() =>
              router.push({
                pathname: '/watch/[login]',
                params: { login: item.broadcaster.login, name: item.broadcaster.displayName },
              })
            }
          />
        )}
      />
    </View>
  );
}

function StreamCard({
  stream,
  colors: c,
  onPress,
}: {
  stream: LiveStream;
  colors: (typeof palette)['dark'];
  onPress: () => void;
}) {
  const { broadcaster } = stream;
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: c.card, opacity: pressed ? 0.85 : 1 },
      ]}
    >
      <View>
        <Image source={{ uri: stream.previewImageURL ?? undefined }} style={styles.preview} />
        <View style={styles.liveBadge}>
          <Text style={styles.liveText}>LIVE</Text>
        </View>
        <View style={styles.viewers}>
          <Text style={styles.viewersText}>{formatViewers(stream.viewersCount)} viewers</Text>
        </View>
      </View>
      <View style={styles.meta}>
        <Image source={{ uri: broadcaster.profileImageURL ?? undefined }} style={styles.avatar} />
        <View style={styles.metaText}>
          <Text numberOfLines={1} style={[styles.streamTitle, { color: c.text }]}>
            {stream.title}
          </Text>
          <Text numberOfLines={1} style={{ color: c.muted }}>
            {broadcaster.displayName}
            {stream.game ? ` · ${stream.game.displayName}` : ''}
          </Text>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    paddingHorizontal: 16,
    paddingBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: { fontSize: 28, fontWeight: '800' },
  liveRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#eb0400' },
  chip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20 },
  list: { paddingHorizontal: 16, gap: 16 },
  empty: { textAlign: 'center', marginTop: 48 },
  card: { borderRadius: 16, overflow: 'hidden' },
  preview: { width: '100%', aspectRatio: 16 / 9, backgroundColor: '#26262c' },
  liveBadge: {
    position: 'absolute',
    top: 10,
    left: 10,
    backgroundColor: '#eb0400',
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  liveText: { color: '#fff', fontSize: 11, fontWeight: '800', letterSpacing: 0.5 },
  viewers: {
    position: 'absolute',
    bottom: 10,
    left: 10,
    backgroundColor: 'rgba(0,0,0,0.7)',
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  viewersText: { color: '#fff', fontSize: 12, fontWeight: '600' },
  meta: { flexDirection: 'row', gap: 12, padding: 12, alignItems: 'center' },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#26262c' },
  metaText: { flex: 1, gap: 2 },
  streamTitle: { fontSize: 15, fontWeight: '700' },
});
