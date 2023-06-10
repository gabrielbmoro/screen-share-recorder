package com.example.screenrecorder.service

import android.content.Intent

enum class RecorderMode {
    SYNC, ASYNC
}

interface ScreenCaptureInterface {

    fun start(resultCode: Int, resultData: Intent, recorderMode: RecorderMode)

    fun stop()
}