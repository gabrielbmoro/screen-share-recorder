package com.example.screenrecorder.service.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.view.Surface
import timber.log.Timber

class MediaRecorderAsyncStrategy(context: Context): MediaRecorderStrategy {
    private val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Timber.d("Media recorder compatible to >= API 31")
        MediaRecorder(context)
    } else {
        Timber.d("Media recorder compatible to < API 31")
        MediaRecorder()
    }

    override fun setup(outputFile: String, widthPixels: Int, heightPixels: Int) {
        mediaRecorder.run {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            setOutputFile(outputFile)
            Timber.d("Output file -> $outputFile")

            setVideoSize(widthPixels, heightPixels)
            Timber.d("Final video dimensions ->  $widthPixels x $heightPixels")

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            prepare()
            Timber.d("Media recorder prepared...")
        }
    }

    override fun getSurface(): Surface? = mediaRecorder.surface

    override fun start() {
        mediaRecorder.start()
    }

    override fun stop() {
        mediaRecorder.stop()
    }

    override fun release() {
        mediaRecorder.release()
    }
}