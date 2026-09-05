package me.shovon.sms2wallet.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import me.shovon.sms2wallet.data.local.dao.PushLogDao
import me.shovon.sms2wallet.data.local.dao.UnmatchedSmsDao
import me.shovon.sms2wallet.data.local.dao.PushLogWithTransaction
import me.shovon.sms2wallet.data.local.entity.UnmatchedSmsEntity

/** Read side of the Activity tab: the push audit log and the unmatched-SMS list. */
class ActivityRepository @Inject constructor(
    private val pushLogDao: PushLogDao,
    private val unmatchedSmsDao: UnmatchedSmsDao,
) {

    fun observeRecentPushLog(limit: Int = RECENT_LOG_LIMIT): Flow<List<PushLogWithTransaction>> =
        pushLogDao.observeRecentWithTransaction(limit)

    fun observeUnmatchedSms(): Flow<List<UnmatchedSmsEntity>> = unmatchedSmsDao.observeAll()

    suspend fun deleteUnmatchedSms(id: Long) = unmatchedSmsDao.deleteById(id)

    suspend fun cleanDuplicates() = unmatchedSmsDao.deleteDuplicates()

    private companion object {
        const val RECENT_LOG_LIMIT = 200
    }
}
