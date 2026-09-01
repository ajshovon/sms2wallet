package me.shovon.sms2wallet.data.sms

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.shovon.sms2wallet.data.prefs.AppPreferences

/**
 * Dependency seam for [SmsBroadcastReceiver].
 *
 * Broadcast receivers are instantiated by the OS, not by Hilt, so they can't use constructor
 * injection or `@AndroidEntryPoint` without extra manifest ceremony; a Hilt
 * [dagger.hilt.EntryPoint] resolved via [dagger.hilt.android.EntryPointAccessors] from the
 * application context is the simplest way to reach the Hilt graph from `onReceive`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsIngestEntryPoint {
    fun appPreferences(): AppPreferences
    fun smsParsingService(): SmsParsingService
    fun ingestSink(): IngestSink
}
