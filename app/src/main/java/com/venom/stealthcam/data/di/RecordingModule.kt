package com.venom.stealthcam.data.di

import com.venom.stealthcam.data.recording.Camera2RecordingRepository
import com.venom.stealthcam.domain.repository.RecordingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Recording data layer.
 * Binds [Camera2RecordingRepository] as the [RecordingRepository] implementation.
 *
 * Scoped as [Singleton] so the [kotlinx.coroutines.flow.StateFlow] is shared across
 * every consumer (Foreground Service, UI, volume trigger) without duplication.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingModule {

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(
        impl: Camera2RecordingRepository
    ): RecordingRepository
}
