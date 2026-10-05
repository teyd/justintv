package dev.teyd.justintv.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.data.PlaybackSettingsStore
import javax.inject.Inject

@HiltViewModel
class PipSettingsViewModel @Inject constructor(
    val store: PlaybackSettingsStore,
) : ViewModel()
