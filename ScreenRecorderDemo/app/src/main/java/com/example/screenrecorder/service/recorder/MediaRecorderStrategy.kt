package com.example.screenrecorder.service.recorder

import android.view.Surface

interface MediaRecorderStrategy {
    fun setup(outputFile: String, widthPixels: Int, heightPixels: Int)
    fun start()
    fun stop()
    fun release()
    fun getSurface(): Surface?
}