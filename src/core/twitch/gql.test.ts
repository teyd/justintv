import { Effect, Result } from 'effect';
import { afterEach, describe, expect, test, vi } from 'vite-plus/test';

import { fetchLiveStreams, mergeStreams, type LiveStream } from './gql';

const stream = (id: string): LiveStream => ({
  id,
  title: id,
  viewersCount: 10,
  previewImageURL: null,
  broadcaster: { login: id, displayName: id, profileImageURL: null },
  game: null,
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('mergeStreams', () => {
  test('deduplicates across pages and within a page without mutating previous content', () => {
    const previous = [stream('a')];
    const merged = mergeStreams(previous, [stream('a'), stream('b'), stream('b')]);

    expect(merged.map((item) => item.id)).toEqual(['a', 'b']);
    expect(previous).toHaveLength(1);
    expect(merged[0]).toBe(previous[0]);
  });

  test('supports replacement with an empty base', () => {
    expect(mergeStreams([], [stream('b'), stream('b')])).toEqual([stream('b')]);
    expect(mergeStreams([], [])).toEqual([]);
  });
});

describe('fetchLiveStreams', () => {
  test('decodes pages and returns the last cursor', async () => {
    const request = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(
        Response.json({ data: { streams: { edges: [{ cursor: 'next', node: stream('a') }] } } }),
      );

    const page = await Effect.runPromise(fetchLiveStreams('previous'));

    expect(page).toEqual({ streams: [stream('a')], cursor: 'next' });
    expect(request.mock.calls[0]?.[1]?.body).toContain('previous');
    expect(request.mock.calls[0]?.[1]?.signal).toBeInstanceOf(AbortSignal);
  });

  test('marks an empty page as exhausted', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      Response.json({ data: { streams: { edges: [] } } }),
    );

    expect(await Effect.runPromise(fetchLiveStreams())).toEqual({ streams: [], cursor: null });
  });

  test('reports HTTP, GraphQL and malformed responses as typed failures', async () => {
    const request = vi.spyOn(globalThis, 'fetch');

    const responses = [
      new Response(null, { status: 503 }),
      Response.json({ errors: [{ message: 'Unavailable' }] }),
      Response.json({ data: { streams: { edges: [{ node: { id: 123 } }] } } }),
    ];

    for (const response of responses) {
      request.mockResolvedValueOnce(response);
      const result = await Effect.runPromise(fetchLiveStreams().pipe(Effect.result));

      expect(Result.isFailure(result)).toBe(true);
    }
  });

  test('aborts the underlying fetch when the caller leaves', async () => {
    let started: (() => void) | undefined;

    const ready = new Promise<void>((resolve) => {
      started = resolve;
    });

    let requestSignal: AbortSignal | null | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_url, options) => {
      requestSignal = options?.signal;
      started?.();

      return new Promise<Response>((_resolve, reject) => {
        requestSignal?.addEventListener('abort', () => reject(new Error('Aborted')), {
          once: true,
        });
      });
    });
    const controller = new AbortController();
    const pending = Effect.runPromiseExit(fetchLiveStreams(), { signal: controller.signal });
    await ready;
    controller.abort();
    await pending;

    expect(requestSignal?.aborted).toBe(true);
  });

  test('times out stalled requests and aborts the transport', async () => {
    vi.useFakeTimers();
    let requestSignal: AbortSignal | null | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_url, options) => {
      requestSignal = options?.signal;

      return new Promise<Response>((_resolve, reject) => {
        requestSignal?.addEventListener('abort', () => reject(new Error('Aborted')), {
          once: true,
        });
      });
    });
    const pending = Effect.runPromise(fetchLiveStreams().pipe(Effect.result));
    await vi.advanceTimersByTimeAsync(15_000);
    const result = await pending;

    expect(Result.isFailure(result)).toBe(true);

    if (Result.isFailure(result)) expect(result.failure.reason).toBe('Twitch request timed out');

    expect(requestSignal?.aborted).toBe(true);
  });
});
