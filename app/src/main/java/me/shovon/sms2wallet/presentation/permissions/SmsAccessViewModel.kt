package me.shovon.sms2wallet.presentation.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.shovon.sms2wallet.data.repository.SmsScanRepository

/**
 * Owns the inbox backfill that has to run once `READ_SMS` is granted.
 *
 * Lives in a ViewModel rather than the permission callback so the scan survives the
 * configuration change that a permission dialog can trigger, and so a rotation midway through a
 * long backfill doesn't restart it.
 */
@HiltViewModel
class SmsAccessViewModel @Inject constructor(
    private val smsScanRepository: SmsScanRepository,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /**
     * Scans the SMS inbox for transactions.
     *
     * Guarded against overlapping runs: the gate calls this on every grant, and the Settings
     * rescan action can fire while one is already in flight.
     */
    fun scanInbox(fromScratch: Boolean = false) {
        if (_scanState.value is ScanState.Scanning) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            _scanState.value = runCatching { smsScanRepository.scanInbox(fromScratch) }
                .fold(
                    onSuccess = { ScanState.Done(examined = it) },
                    // A SecurityException here means the permission was revoked between the
                    // grant check and the read; surface it rather than silently reporting zero.
                    onFailure = { ScanState.Failed(it.message ?: "Could not read the SMS inbox") },
                )
        }
    }

    sealed interface ScanState {
        data object Idle : ScanState
        data object Scanning : ScanState
        data class Done(val examined: Int) : ScanState
        data class Failed(val message: String) : ScanState
    }
}
