package com.example.screenrecorder.service

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.view.Surface
import timber.log.Timber

class RecordManager(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val outputFile: String,
    private val callback: Callback
) {

    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null

    fun start(context: Context) {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Timber.d("Media recorder compatible to >= API 31")
                MediaRecorder(context)
            } else {
                Timber.d("Media recorder compatible to < API 31")
                MediaRecorder()
            }

            mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            mediaRecorder!!.setOutputFile(outputFile)
            Timber.d("Output file -> $outputFile")

            val widthPixels = if (displayMetrics.widthPixels > MAX_WIDTH_SUPPORTED) {
                Timber.d("Display metrics exceed the width limit...")
                MAX_WIDTH_SUPPORTED
            } else {
                displayMetrics.widthPixels
            }

            val heightPixels = if (displayMetrics.heightPixels > MAX_HEIGHT_SUPPORTED) {
                Timber.d("Display metrics exceed the height limit...")
                MAX_HEIGHT_SUPPORTED
            } else {
                displayMetrics.heightPixels
            }

            mediaRecorder!!.setVideoSize(widthPixels, heightPixels)
            Timber.d("Final video dimensions ->  $widthPixels x $heightPixels")

            mediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            mediaRecorder!!.prepare()
            Timber.d("Media recorder prepared...")

            surface = mediaRecorder!!.surface

            virtualDisplay = mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                widthPixels,
                heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Timber.d("Virtual display paused...")
                    }

                    override fun onResumed() {
                        Timber.d("Virtual display resumed...")

                        mediaRecorder!!.start()
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

    fun stop() {
        try {
            mediaRecorder?.stop()
            Timber.d("Record stopped...")

            mediaRecorder?.release()
            Timber.d("Media recorder released...")

            mediaRecorder = null

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