import { Data, Effect, Schema } from 'effect';

const GQL_URL = 'https://gql.twitch.tv/gql';
// Public web client id; works without login.
const CLIENT_ID = 'kimne78kx3ncx6brgo4mv6wki5h1ko';

export class TwitchError extends Data.TaggedError('TwitchError')<{ reason: string }> {}

const gql = <A, I>(query: string, schema: Schema.Codec<A, I>) =>
  Effect.tryPromise({
    try: async () => {
      const res = await fetch(GQL_URL, {
        method: 'POST',
        headers: { 'Client-Id': CLIENT_ID, 'Content-Type': 'application/json' },
        body: JSON.stringify({ query }),
      });
      if (!res.ok) throw new Error(`GQL responded ${res.status}`);
      const json = (await res.json()) as { data?: unknown; errors?: { message: string }[] };
      if (json.errors?.length) throw new Error(json.errors[0].message);
      return json.data;
    },
    catch: (e) => new TwitchError({ reason: e instanceof Error ? e.message : String(e) }),
  }).pipe(
    Effect.flatMap((data) =>
      Schema.decodeUnknownEffect(schema)(data).pipe(
        Effect.mapError(() => new TwitchError({ reason: 'Unexpected response from Twitch' })),
      ),
    ),
  );

const LiveStream = Schema.Struct({
  id: Schema.String,
  title: Schema.NullOr(Schema.String),
  viewersCount: Schema.NullOr(Schema.Number),
  previewImageURL: Schema.NullOr(Schema.String),
  broadcaster: Schema.Struct({
    login: Schema.String,
    displayName: Schema.String,
    profileImageURL: Schema.NullOr(Schema.String),
  }),
  game: Schema.NullOr(Schema.Struct({ displayName: Schema.String })),
});
export type LiveStream = typeof LiveStream.Type;

const StreamsData = Schema.Struct({
  streams: Schema.Struct({
    edges: Schema.Array(Schema.Struct({ cursor: Schema.String, node: LiveStream })),
  }),
});

export const fetchLiveStreams = (after?: string | null) =>
  gql(
    `query {
      streams(first: 24${after ? `, after: ${JSON.stringify(after)}` : ''}) {
        edges {
          cursor
          node {
            id
            title
            viewersCount
            previewImageURL(width: 640, height: 360)
            broadcaster { login displayName profileImageURL(width: 70) }
            game { displayName }
          }
        }
      }
    }`,
    StreamsData,
  ).pipe(
    Effect.map((d) => ({
      streams: d.streams.edges.map((e) => e.node),
      cursor: d.streams.edges.at(-1)?.cursor ?? null,
    })),
  );

const TokenData = Schema.Struct({
  streamPlaybackAccessToken: Schema.NullOr(
    Schema.Struct({ value: Schema.String, signature: Schema.String }),
  ),
});

/** Resolves a channel's HLS master playlist URL on Twitch's usher host. */
export const fetchPlaybackUrl = (login: string) =>
  gql(
    `query {
      streamPlaybackAccessToken(
        channelName: ${JSON.stringify(login.toLowerCase())},
        params: { platform: "web", playerBackend: "mediaplayer", playerType: "site" }
      ) { value signature }
    }`,
    TokenData,
  ).pipe(
    Effect.flatMap((d) => {
      const token = d.streamPlaybackAccessToken;
      if (!token) return Effect.fail(new TwitchError({ reason: 'Channel is offline' }));
      const params = new URLSearchParams({
        sig: token.signature,
        token: token.value,
        allow_source: 'true',
        allow_audio_only: 'true',
        player: 'twitchweb',
        playlist_include_framerate: 'true',
        p: String(Math.floor(Math.random() * 1_000_000)),
      });
      return Effect.succeed(
        `https://usher.ttvnw.net/api/channel/hls/${login.toLowerCase()}.m3u8?${params}`,
      );
    }),
  );

export const formatViewers = (n: number | null) => {
  const v = n ?? 0;
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
  if (v >= 10_000) return `${Math.round(v / 1000)}K`;
  if (v >= 1000) return `${(v / 1000).toFixed(1)}K`;
  return String(v);
};
