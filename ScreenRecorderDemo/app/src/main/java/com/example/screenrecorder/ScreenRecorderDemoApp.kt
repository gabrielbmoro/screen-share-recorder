package com.example.screenrecorder

import android.app.Application
import timber.log.Timber
class ScreenRecorderDemoApp: Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
    }
}