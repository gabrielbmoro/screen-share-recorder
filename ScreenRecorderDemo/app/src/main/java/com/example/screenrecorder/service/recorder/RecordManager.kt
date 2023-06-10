package com.example.screenrecorder.service.recorder

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.Surface
import com.example.screenrecorder.service.RecorderMode
import timber.log.Timber

class RecordManager(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val outputFile: String,
    private val callback: Callback
) {

    private var mediaRecorderStrategy: MediaRecorderStrategy? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var commonSurface: Surface? = null

    fun start(context: Context, strategy: RecorderMode) {
        val widthPixels = widthNormalized()
        val heightPixels = heightNormalized()

        try {
            mediaRecorderStrategy = when (strategy) {
                RecorderMode.ASYNC -> MediaRecorderAsyncStrategy(context)
                RecorderMode.SYNC -> MediaRecorderSyncStrategy()
            }
            mediaRecorderStrategy!!.setup(
                outputFile = outputFile,
                widthPixels = widthNormalized(),
                heightPixels = heightNormalized()
            )
            val surface = mediaRecorderStrategy!!.getSurface()

            commonSurface = surface

            virtualDisplay = mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                widthPixels,
                heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                commonSurface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Timber.d("Virtual display paused...")
                    }

                    override fun onResumed() {
                        Timber.d("Virtual display resumed...")

                        mediaRecorderStrategy?.start()
                        Timber.d("Record started...")
                        callback.onRecordStarted()
                    }

                    override fun onStopped() {
                        Timber.d("Virtual display stopped...")
                    }
                },
                null
            )
        } catch (exception: Exception) {
            Timber.e(exception)
            callback.onError(exception)
        }
    }

    private fun heightNormalized() = if (displayMetrics.heightPixels > MAX_HEIGHT_SUPPORTED) {
        Timber.d("Display metrics exceed the height limit...")
        MAX_HEIGHT_SUPPORTED
    } else {
        displayMetrics.heightPixels
    }

    private fun widthNormalized() = if (displayMetrics.widthPixels > MAX_WIDTH_SUPPORTED) {
        Timber.d("Display metrics exceed the width limit...")
        MAX_WIDTH_SUPPORTED
    } else {
        displayMetrics.widthPixels
    }

    fun stop() {
        try {
            mediaRecorderStrategy?.run {
                stop()
                Timber.d("Record stopped...")

                release()
                Timber.d("Media recorder released...")
            }
            mediaRecorderStrategy = null

            virtualDisplay?.release()
            Timber.d("Virtual display released...")

            virtualDisplay = null

            mediaProjection.stop()
            Timber.d("Media projection stopped...")

            Timber.d("Media projection stopped...")

            Timber.d("Record saved")
            callback.onRecordSaved()
        } catch (exception: Exception) {
            Timber.e(exception)
            callback.onError(exception)
        }
    }

    interface Callback {
        fun onRecordStarted()

        fun onRecordSaved()

        fun onError(exception: Exception)
    }

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "virtual display"
        private const val MAX_WIDTH_SUPPORTED = 720
        private const val MAX_HEIGHT_SUPPORTED = 1520
    }
}