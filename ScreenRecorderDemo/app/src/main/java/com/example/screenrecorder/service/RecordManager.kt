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
                MediaRecorder(context)
            } else MediaRecorder()

            mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder!!.setOutputFile(outputFile)
            mediaRecorder!!.setVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
            mediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            mediaRecorder!!.prepare()

            surface = mediaRecorder!!.surface

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "virtual display",
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() { }

                    override fun onResumed() {
                        mediaRecorder!!.start()

                        Timber.d("record started")
                        callback.onRecordStarted()
                    }

                    override fun onStopped() { }
                },
                null
            )
        } catch (exception: Exception) {
            Timber.e("record error", exception)
            callback.onError(exception)
        }
    }

    fun stop() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection.stop()

            Timber.d("record saved")
            callback.onRecordSaved()
        } catch (exception: Exception) {
            callback.onError(exception)
        }
    }

    interface Callback {
        fun onRecordStarted()

        fun onRecordSaved()

        fun onError(exception: Exception)
    }
}