package me.shovon.sms2wallet.data.sms

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [IngestSink] to the no-op default so the ingest pipeline compiles and runs before a
 * persistence-backed sink exists. Replace [NoOpIngestSink] with the Room-backed implementation
 * here once it lands - every consumer already depends on the [IngestSink] interface.
 */
@Module
@InstallIn(SingletonComponent::class)
object SmsIngestModule {

    @Provides
    @Singleton
    fun provideIngestSink(): IngestSink = NoOpIngestSink()
}
