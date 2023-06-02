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
import java.lang.RuntimeException

class RecordManager(
    private val mediaProjection: MediaProjection,
    private val displayMetrics: DisplayMetrics,
    private val outputFile: String
) {

    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null

    fun start(context: Context) {
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
                override fun onPaused() {
                    Timber.d("Virtual display -> onPaused")
                }

                override fun onResumed() {
                    // Start the MediaCodec encoder
                    Timber.d("Virtual display -> onResume")
                    mediaRecorder!!.start()
                }

                override fun onStopped() {
                    Timber.d("Virtual display -> onStopped")
                }
            },
            null
        )
    }

    fun stop() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection.stop()
        } catch (runtimeException: RuntimeException) {
            Timber.e(runtimeException)
        }
    }
}