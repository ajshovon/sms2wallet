package me.shovon.sms2wallet.data.local

import androidx.room.TypeConverter
import me.shovon.sms2wallet.domain.model.PushState

/**
 * Entities store [PushState] as its plain enum-constant name in a `TEXT` column (see
 * [me.shovon.sms2wallet.data.local.entity.TransactionEntity.pushState]) rather than as a
 * Kotlin enum, so the value is trivially readable in a raw `sqlite3` shell for debugging. DAO
 * methods, however, take [PushState] directly as query bind parameters for type-safety - this
 * converter is what lets Room translate between the two.
 */
class Converters {
    @TypeConverter
    fun pushStateToString(state: PushState): String = state.name

    @TypeConverter
    fun stringToPushState(value: String): PushState = PushState.valueOf(value)
}
