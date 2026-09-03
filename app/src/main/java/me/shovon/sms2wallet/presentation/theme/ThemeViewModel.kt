package me.shovon.sms2wallet.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.shovon.sms2wallet.data.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import me.shovon.sms2wallet.domain.model.AccentColor
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

    /** Both appearance choices together, so the activity recomposes once when either changes. */
    data class Appearance(val themeMode: ThemeMode, val accentColor: AccentColor)

    val appearance: StateFlow<Appearance?> = combine(
        settingsRepository.themeMode,
        settingsRepository.accentColor,
    ) { mode, accent -> Appearance(mode, accent) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // null means "not read yet". The activity holds the splash screen until this resolves,
            // so the app never paints light-then-dark on launch.
            initialValue = null,
        )
}
