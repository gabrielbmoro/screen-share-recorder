package com.example.screensharetest

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureForegroundService : Service() {
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_RECORDING_INTENT_ACTION -> {
                val notification = baseNotification()

                startForeground(1, notification)

                val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

                val resultCode = intent.getIntExtra(RESULT_CODE_KEY, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(RESULT_DATA_KEY)
                mediaProjection =
                    mediaProjectionManager.getMediaProjection(resultCode, resultData!!)

                mediaProjection?.registerCallback(
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            virtualDisplay?.release()
                            Log.d(TAG, "MediaProjection.onStop()")
                        }
                    },
                    Handler(Looper.myLooper()!!)
                )

                val metrics = resources.displayMetrics

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "Screen 1",
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null,
                    object : VirtualDisplay.Callback() {
                        override fun onStopped() {
                            Log.d(TAG, "VirtualDisplay.onStop()")
                        }

                        override fun onPaused() {
                            Log.d(TAG, "VirtualDisplay.onPaused()")
                        }

                        override fun onResumed() {
                            Log.d(TAG, "VirtualDisplay.onResumed()")
                        }
                    },
                    null
                )

                return START_STICKY
            }

            STOP_RECORDING_INTENT_ACTION -> {
                mediaProjection?.stop()
                virtualDisplay?.release()

                mediaProjection = null
                virtualDisplay = null

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