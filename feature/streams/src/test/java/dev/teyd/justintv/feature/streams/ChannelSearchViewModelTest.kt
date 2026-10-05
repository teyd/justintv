package dev.teyd.justintv.feature.streams

import com.google.common.truth.Truth.assertThat
import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.model.ChannelPresence
import dev.teyd.justintv.core.model.Game
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.network.ChannelLive
import dev.teyd.justintv.core.network.DirectorySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSearchViewModelTest {
    @Test
    fun `fewer than four characters never calls twitch`() =
        runTest {
            val directory = FakeDirectory()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = ChannelSearchViewModel(directory)

                viewModel.onQueryChange("xqc")
                advanceTimeBy(ChannelSearch.DEBOUNCE_MS + 1)
                advanceUntilIdle()

                assertThat(directory.searches).isEmpty()
                assertThat(viewModel.state.value.hits).isEmpty()
                assertThat(viewModel.state.value.isSearching).isFalse()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `four characters search once the typing pause elapses`() =
        runTest {
            val directory = FakeDirectory()
            directory.results = listOf(shrood)
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = ChannelSearchViewModel(directory)

                viewModel.onQueryChange("shro")
                advanceTimeBy(ChannelSearch.DEBOUNCE_MS - 1)
                assertThat(directory.searches).isEmpty()

                advanceTimeBy(1)
                advanceUntilIdle()

                assertThat(directory.searches).containsExactly("shro")
                assertThat(viewModel.state.value.hits).containsExactly(shrood)
                assertThat(viewModel.state.value.isSearching).isFalse()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `a newer query cancels the search still in flight`() =
        runTest {
            val directory = FakeDirectory()
            val gate = CompletableDeferred<List<ChannelHit>>()
            directory.gate = gate
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = ChannelSearchViewModel(directory)

                viewModel.onQueryChange("shro")
                advanceTimeBy(ChannelSearch.DEBOUNCE_MS)
                advanceUntilIdle()
                assertThat(directory.searches).containsExactly("shro")

                viewModel.onQueryChange("shroo")
                advanceTimeBy(ChannelSearch.DEBOUNCE_MS)
                advanceUntilIdle()

                assertThat(directory.cancelled).isTrue()
                assertThat(directory.searches).containsExactly("shro", "shroo").inOrder()
                assertThat(viewModel.state.value.hits).isEmpty()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `a failed search keeps the field and reports the error`() =
        runTest {
            val directory = FakeDirectory()
            directory.error = IllegalStateException("Twitch rejected the request")
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = ChannelSearchViewModel(directory)

                viewModel.onQueryChange("shroud")
                advanceTimeBy(ChannelSearch.DEBOUNCE_MS)
                advanceUntilIdle()

                assertThat(viewModel.state.value.query).isEqualTo("shroud")
                assertThat(viewModel.state.value.hits).isEmpty()
                assertThat(viewModel.state.value.error).isEqualTo("Twitch rejected the request")
                assertThat(viewModel.state.value.isSearching).isFalse()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class FakeDirectory : DirectorySource {
        val searches = mutableListOf<String>()
        var results: List<ChannelHit> = emptyList()
        var error: Exception? = null
        var gate: CompletableDeferred<List<ChannelHit>>? = null
        var cancelled = false

        override suspend fun searchChannels(query: String): List<ChannelHit> {
            searches += query
            val pending = gate
            if (pending != null) {
                gate = null
                try {
                    return pending.await()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
            error?.let { throw it }
            return results
        }

        override suspend fun topStreams(languages: Set<String>): List<LiveStream> = emptyList()

        override suspend fun gameStreams(
            gameName: String,
            languages: Set<String>,
        ): List<LiveStream> = emptyList()

        override suspend fun topGames(): List<Game> = emptyList()

        override suspend fun channelLive(login: String): ChannelLive? = null
    }

    private companion object {
        val shrood =
            ChannelHit(
                id = "2",
                login = "shrood",
                displayName = "shrood",
                avatarUrl = null,
                presence = ChannelPresence.Live("24/7 VODS", 116),
            )
    }
}
