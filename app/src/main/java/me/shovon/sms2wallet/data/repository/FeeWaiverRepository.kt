package me.shovon.sms2wallet.data.repository

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import me.shovon.sms2wallet.data.local.dao.TransactionDao
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.domain.model.FeeWaiverProgress
import me.shovon.sms2wallet.domain.model.FeeWaiverTracker

/**
 * Cards tracked towards an annual-fee waiver, and how far each has got this fee year.
 *
 * The trackers themselves live in DataStore with the rest of the user's settings; the counts
 * come live from Room, so a new card SMS moves the dashboard without a refresh.
 */
class FeeWaiverRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val transactionDao: TransactionDao,
) {

    val trackers: Flow<List<FeeWaiverTracker>> = appPreferences.feeWaiverTrackers

    suspend fun save(tracker: FeeWaiverTracker) = appPreferences.saveFeeWaiverTracker(tracker)

    suspend fun remove(bankName: String, accountLast4: String) =
        appPreferences.removeFeeWaiverTracker(bankName, accountLast4)

    /**
     * Progress for every tracked card, re-evaluated at local midnight: a fee year can end on any
     * day, and the next one has to start counting from zero without the app being reopened.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeProgress(): Flow<List<FeeWaiverProgress>> =
        combine(trackers, currentDate()) { trackers, today -> trackers to today }
            .flatMapLatest { (trackers, today) ->
                // combine() over an empty list never emits, which would leave the dashboard
                // waiting forever instead of showing "no cards tracked".
                if (trackers.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(trackers.map { observeProgress(it, today) }) { it.toList() }
                }
            }

    private fun observeProgress(tracker: FeeWaiverTracker, today: LocalDate): Flow<FeeWaiverProgress> {
        val zone = ZoneId.systemDefault()
        val period = tracker.periodContaining(today)
        val fromMillis = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = period.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return transactionDao.observeCardTransactionCount(
            bankName = tracker.bankName,
            accountLast4 = tracker.accountLast4,
            types = FeeWaiverTracker.COUNTED_TYPES.map { it.name },
            fromMillis = fromMillis,
            toMillis = toMillis,
        ).map { count -> FeeWaiverProgress(tracker, period, count, today) }
    }

    /** Today's date, emitted again each time the local day changes. */
    private fun currentDate(): Flow<LocalDate> = flow {
        while (currentCoroutineContext().isActive) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay(maxOf(nextMidnight - System.currentTimeMillis(), MIN_TICK_MILLIS))
        }
    }.distinctUntilChanged()

    private companion object {
        const val MIN_TICK_MILLIS = 1_000L
    }
}
