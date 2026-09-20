package com.venom.stealthcam.core.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the application-scoped [kotlinx.coroutines.CoroutineScope].
 * This scope is tied to the process lifetime — it is never cancelled as long as
 * the app process is alive, making it safe for Singleton components like
 * [com.venom.stealthcam.data.recording.Camera2RecordingRepository] to use for
 * launching async operations (e.g. auto-stop on max duration reached).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
