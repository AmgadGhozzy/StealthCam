package com.venom.stealthcam.data.di

import android.content.Context
import android.hardware.camera2.CameraManager
import com.venom.stealthcam.data.camera.Camera2CameraRepository
import com.venom.stealthcam.domain.repository.CameraRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Camera2 data layer.
 *
 * Why abstract class with companion object?
 * — [Binds] functions must be abstract (Hilt generates the delegation call at compile time).
 * — [Provides] functions must be in a concrete context.
 * — An abstract class with a companion object satisfies both constraints in one module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    /**
     * Binds [Camera2CameraRepository] as the [CameraRepository] implementation.
     * Scoped as [Singleton] so the capability cache lives for the process lifetime.
     */
    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        impl: Camera2CameraRepository
    ): CameraRepository

    companion object {

        /**
         * Provides the system [CameraManager] as a singleton.
         * Centralised here so every component that needs it (repository, future
         * recording engine) gets the same instance without context leaks.
         */
        @Provides
        @Singleton
        fun provideCameraManager(
            @ApplicationContext context: Context
        ): CameraManager = context.getSystemService(CameraManager::class.java)
    }
}
