package com.example.screenrecorder.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.screenrecorder.R
import java.io.File

class ScreenCaptureForegroundService : Service() {

    private var recordHandler: RecordHandler? = null
    private val recordThread: HandlerThread = HandlerThread("record handler")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_RECORDING_INTENT_ACTION -> {
                val notification = baseNotification()

                startForeground(1, notification)

                val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

                val resultCode = intent.getIntExtra(RESULT_CODE_KEY, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(RESULT_DATA_KEY)
                val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)

                recordThread.start()

                val outputFile = applicationContext.filesDir.absolutePath + File.separator + "recorded.mp4"
                this.recordHandler = RecordHandler(
                    recordThread.looper,
                    mediaProjection,
                    outputFile,
                    resources.displayMetrics
                )
                this.recordHandler?.handleMessage(RecordHandler.startMessage())

                return START_STICKY
            }

            STOP_RECORDING_INTENT_ACTION -> {
                this.recordHandler?.handleMessage(RecordHandler.stopMessage())

                stopSelf()
                return START_NOT_STICKY
            }

            else -> return START_STICKY
        }
    }

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

        recordThread.quitSafely()
        recordHandler = null

        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ScreenCaptureServiceChannel"
        private const val START_RECORDING_INTENT_ACTION = "start_recording"
        private const val STOP_RECORDING_INTENT_ACTION = "stop_recording"
        private const val RESULT_CODE_KEY = "resultCodeKey"
        private const val RESULT_DATA_KEY = "resultDataKey"

        fun createStartRecordingIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent
        ): Intent {
            return Intent(context, ScreenCaptureForegroundService::class.java).apply {
                putExtra(RESULT_CODE_KEY, resultCode)
                putExtra(RESULT_DATA_KEY, resultData)

                action = START_RECORDING_INTENT_ACTION
            }
        }

        fun createStopRecordingIntent(
            context: Context
        ): Intent {
            return Intent(context, ScreenCaptureForegroundService::class.java).apply {
                action = STOP_RECORDING_INTENT_ACTION
            }
        }
    }
}