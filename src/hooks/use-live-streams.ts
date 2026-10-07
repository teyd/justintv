import { Effect, Result } from 'effect';
import { useCallback, useEffect, useRef, useState } from 'react';

import { fetchLiveStreams, mergeStreams, type LiveStream } from '@/core/twitch/gql';

export function useLiveStreams() {
  const [streams, setStreams] = useState<LiveStream[]>([]);
  const [failure, setFailure] = useState<{ reason: string; reset: boolean } | null>(null);
  const [refreshing, setRefreshing] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const cursor = useRef<string | null>(null);
  const request = useRef<AbortController | null>(null);

  const load = useCallback(async (reset: boolean) => {
    if (!reset && (request.current || cursor.current === null)) return;

    // Refresh supersedes pagination; a superseded response must never replace the new feed.
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setRefreshing(reset);
    setLoadingMore(!reset);
    setFailure(null);

    try {
      const result = await Effect.runPromise(
        fetchLiveStreams(reset ? null : cursor.current).pipe(Effect.result),
        { signal: controller.signal },
      );

      if (controller.signal.aborted) return;

      if (Result.isSuccess(result)) {
        const page = result.success;
        cursor.current = page.cursor;
        setStreams((previous) => mergeStreams(reset ? [] : previous, page.streams));
      } else {
        setFailure({ reason: result.failure.reason, reset });
      }
    } catch (failure) {
      if (!controller.signal.aborted) {
        setFailure({
          reason: failure instanceof Error ? failure.message : 'Unable to load streams',
          reset,
        });
      }
    } finally {
      if (request.current === controller) {
        request.current = null;
        setRefreshing(false);
        setLoadingMore(false);
      }
    }
  }, []);

  useEffect(() => {
    void load(true);

    return () => {
      request.current?.abort();
      request.current = null;
    };
  }, [load]);

  const retry = () => load(failure?.reset ?? true);

  return {
    streams,
    error: failure?.reason ?? null,
    isRefreshError: failure?.reset ?? false,
    refreshing,
    loadingMore,
    load,
    retry,
  };
}
