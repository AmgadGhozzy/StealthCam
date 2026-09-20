package com.venom.stealthcam

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Custom Application class required by Hilt.
 * @HiltAndroidApp triggers Hilt's code generation and creates the application-level
 * dependency container (SingletonComponent). All @Singleton scoped bindings live here.
 */
@HiltAndroidApp
class StealthCamApp : Application()
