package com.venom.stealthcam.data.di

import com.venom.stealthcam.data.storage.MediaStoreStorageRepository
import com.venom.stealthcam.domain.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        impl: MediaStoreStorageRepository
    ): StorageRepository
}
