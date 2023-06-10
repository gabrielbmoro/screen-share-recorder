package com.example.screenrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.screenrecorder.R
import com.example.screenrecorder.service.recorder.RecordManager
import java.io.File

class ScreenCaptureForegroundService : Service(), ScreenCaptureInterface {

    private var recordManager: RecordManager? = null
    private val binder: IBinder = ScreenCaptureBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    private fun baseNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Screen share")
            .setContentText("Running Media projection...")
            .setSmallIcon(R.drawable.ic_warning)
            .build()
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun start(resultCode: Int, resultData: Intent, recorderMode: RecorderMode) {
        val notification = baseNotification()

        startForeground(1, notification)

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        val mediaProjection = mediaProjectionManager.getMediaProjection(
            resultCode,
            resultData
        )

        val outputFile = applicationContext.filesDir.absolutePath +
                File.separator +
                System.currentTimeMillis() +
                "_${recorderMode.name}.mp4"

        this.recordManager = RecordManager(
            mediaProjection,
            resources.displayMetrics,
            outputFile,
            object : RecordManager.Callback {
                override fun onRecordStarted() {
                    Toast.makeText(
                        this@ScreenCaptureForegroundService,
                        "Record started",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onRecordSaved() {
                    Toast.makeText(
                        this@ScreenCaptureForegroundService,
                        "Record successfully saved",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: Exception) {
                    Toast.makeText(
                        this@ScreenCaptureForegroundService,
                        "Error...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        this.recordManager?.start(applicationContext, strategy = recorderMode)
    }

    override fun stop() {
        this.recordManager?.stop()
        this.recordManager = null
    }

    inner class ScreenCaptureBinder : Binder() {
        val contract: ScreenCaptureInterface
            get() = this@ScreenCaptureForegroundService
    }

    companion object {
        const val CHANNEL_ID = "ScreenCaptureServiceChannel"
    }
}