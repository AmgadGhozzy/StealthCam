package com.venom.stealthcam.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.venom.stealthcam.core.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Top-level extension property for DataStore. Declared at file scope (not inside a class)
 * as required by the DataStore delegate API. The [preferencesDataStore] delegate is
 * process-safe and creates only one instance per process.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.SETTINGS_DATASTORE_NAME
)

/**
 * Provides infrastructure-level singletons that are available across the entire app lifetime.
 *
 * Design decisions:
 * — [SingletonComponent] scope matches the Application lifecycle — correct for DataStore
 *   which must survive Activity recreation and screen-off periods.
 * — Repository bindings are declared in their own feature-specific modules
 *   (CameraModule, StorageModule, SettingsModule) to maintain modularity and make
 *   each phase independently testable.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * The single DataStore<Preferences> instance for the entire application.
     * All settings reads/writes go through [SettingsRepository] which receives
     * this via injection — no other component accesses DataStore directly.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    /**
     * Application-scoped [CoroutineScope] backed by [SupervisorJob] so that
     * individual child job failures do not cancel sibling jobs.
     * Lives for the entire process lifetime — never cancelled.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
