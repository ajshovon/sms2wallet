package me.shovon.sms2wallet.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.shovon.sms2wallet.data.repository.TransactionRepository
import javax.inject.Inject

/**
 * Provides navigation-level indicators, like the live pending review badge count for the bottom bar.
 */
@HiltViewModel
class RootNavViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val pendingReviewCount: StateFlow<Int> = transactionRepository.observeReviewQueue()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0,
        )
}
