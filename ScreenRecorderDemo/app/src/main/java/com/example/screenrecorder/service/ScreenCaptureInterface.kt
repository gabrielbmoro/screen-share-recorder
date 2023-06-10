package com.example.screenrecorder.service

import android.content.Intent

enum class RecorderMode {
    SYNC, ASYNC
}

interface ScreenCaptureInterface {

    fun startRecorder(resultCode: Int, resultData: Intent, recorderMode: RecorderMode)

    fun stopRecorder()
}