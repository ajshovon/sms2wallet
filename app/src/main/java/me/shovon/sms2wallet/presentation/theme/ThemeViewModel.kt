package me.shovon.sms2wallet.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.shovon.sms2wallet.data.repository.SettingsRepository
import me.shovon.sms2wallet.domain.model.ThemeMode

/**
 * Holds the chosen [ThemeMode] for the whole app.
 *
 * Owned at the activity level rather than by the Settings screen, because the theme has to be
 * known before any screen composes - reading it further down would mean the app painting in one
 * appearance and then switching.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode?> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // null means "not read yet". The activity holds the splash screen until this resolves,
            // so the app never paints light-then-dark on launch.
            initialValue = null,
        )
}
