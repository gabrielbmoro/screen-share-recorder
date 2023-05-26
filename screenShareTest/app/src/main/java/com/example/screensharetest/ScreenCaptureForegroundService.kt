package com.example.screensharetest

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat

interface IScreenCaptureForegroundService {
    fun stopScreenShare()
}

class ScreenCaptureForegroundService : Service(), IScreenCaptureForegroundService {
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(RESULT_CODE_KEY, Activity.RESULT_CANCELED)
        val resultData = intent?.getParcelableExtra<Intent>(RESULT_DATA_KEY)
        val surface = intent?.getParcelableExtra<Surface>(SURFACE_KEY)

        if (surface != null && resultCode != null && resultData != null) {
            startForegroundService(surface, resultCode, resultData)
        }
        return START_STICKY
    }

    private fun startForegroundService(surface: Surface, resultCode: Int, resultData: Intent) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Screen share")
            .setContentText("Running Media projection...")
            .setSmallIcon(R.drawable.ic_warning)


        startForeground(1, builder.build())

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

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
            surface,
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
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }


    override fun stopScreenShare() {
        mediaProjection?.stop()
        virtualDisplay?.release()

        mediaProjection = null
        virtualDisplay = null
    }


    inner class LocalBinder : Binder() {
        fun service(): IScreenCaptureForegroundService = this@ScreenCaptureForegroundService
    }

    companion object {
        const val CHANNEL_ID = "ScreenCaptureServiceChannel"
        private const val SURFACE_KEY = "surfaceKey"
        private const val RESULT_CODE_KEY = "resultCodeKey"
        private const val RESULT_DATA_KEY = "resultDataKey"

        fun createScreenCaptureIntent(
            context: Context,
            surface: Surface,
            resultCode: Int,
            resultData: Intent
        ): Intent {
            return Intent(context, ScreenCaptureForegroundService::class.java).apply {
                putExtra(SURFACE_KEY, surface)
                putExtra(RESULT_CODE_KEY, resultCode)
                putExtra(RESULT_DATA_KEY, resultData)
            }
        }
    }
}