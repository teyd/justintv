package dev.teyd.justintv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.teyd.justintv.core.data.AppearanceSettingsStore
import dev.teyd.justintv.core.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The saved appearance, so the activity can theme itself before the first screen draws. */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        store: AppearanceSettingsStore,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            store.themeMode.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ThemeMode.System,
            )

        val dynamicColor: StateFlow<Boolean> =
            store.dynamicColor.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                false,
            )
    }
