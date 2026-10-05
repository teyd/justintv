package dev.teyd.justintv.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.network.AuthState
import dev.teyd.justintv.core.network.TwitchSession
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val session: TwitchSession,
    ) : ViewModel() {
        val state: StateFlow<AuthState> = session.state

        fun start() = session.start()

        fun logout() = session.logout()
    }
