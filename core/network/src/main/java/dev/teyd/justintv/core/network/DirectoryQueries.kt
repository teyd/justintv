package dev.teyd.justintv.core.network

import dev.teyd.justintv.core.model.StreamLanguages
import kotlinx.serialization.json.JsonPrimitive

/**
 * GraphQL query text for the directory (browse) screens.
 *
 * Anonymous GraphQL has two hard limits that shape these queries: `streams` accepts at most
 * 30 per request, and any pagination cursor triggers Twitch's integrity check. So every query
 * here is a single first page. Full pagination arrives with login, through the Helix API.
 */
object DirectoryQueries {
    const val MAX_STREAMS = 30
    const val MAX_GAMES = 100

    private const val STREAM_FIELDS =
        "id title viewersCount createdAt previewImageURL(width:440,height:248) " +
            "broadcaster{login displayName profileImageURL(width:70) broadcastSettings{language}} " +
            "game{name displayName}"

    fun topStreams(
        languages: Collection<String>,
        limit: Int = MAX_STREAMS,
    ): String {
        val enumList = StreamLanguages.sanitize(languages).joinToString(",")
        val options = if (enumList.isEmpty()) "" else ", options:{languages:[$enumList]}"
        return "query{streams(first:${limit.coerceIn(1, MAX_STREAMS)}$options){edges{node{$STREAM_FIELDS}}}}"
    }

    /** `game.streams` takes language codes as strings, unlike `streams`, which takes an enum. */
    fun gameStreams(
        gameName: String,
        languages: Collection<String>,
        limit: Int = MAX_STREAMS,
    ): String {
        val stringList = StreamLanguages.sanitize(languages).joinToString(",") { "\"$it\"" }
        val options = if (stringList.isEmpty()) "" else ", options:{languages:[$stringList]}"
        return "query{game(name:${JsonPrimitive(gameName)}){streams(first:${limit.coerceIn(1, MAX_STREAMS)}$options)" +
            "{edges{node{$STREAM_FIELDS}}}}}"
    }

    /** Viewers and start time for one channel. `stream` is null when it is offline. */
    fun channelStream(login: String): String = "query{user(login:${JsonPrimitive(login.lowercase())}){stream{viewersCount createdAt}}}"

    fun topGames(limit: Int = MAX_GAMES): String =
        "query{games(first:${limit.coerceIn(1, MAX_GAMES)}, options:{sort:VIEWER_COUNT})" +
            "{edges{node{id name displayName boxArtURL(width:285,height:380) viewersCount}}}}"

    /**
     * Channel typeahead.
     *
     * `searchSuggestions` drops the stream title and often returns an empty suggestion.
     * `searchFor` returns the channels themselves, live and offline, which is what the
     * autocomplete row needs. Anonymous GraphQL requires a platform string; `web` is the
     * one this client id is allowed to send.
     */
    fun searchChannels(query: String): String =
        "query{searchFor(userQuery:${JsonPrimitive(query)}, platform:\"$SEARCH_PLATFORM\")" +
            "{channels{edges{item{__typename ... on User{id login displayName " +
            "profileImageURL(width:70) stream{id title viewersCount}}}}}}}"

    private const val SEARCH_PLATFORM = "web"
}
