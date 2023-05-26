package com.example.screensharetest

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts

const val TAG = "ScreenShare"


class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var screenSharePermissionResult: ActivityResult? = null

    private var screenCaptureService: IScreenCaptureForegroundService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            screenCaptureService =
                (service as? ScreenCaptureForegroundService.LocalBinder)?.service()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenCaptureService = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.surfaceView = findViewById(R.id.surface_view)
        this.startButton = findViewById(R.id.start_button)
        this.stopButton = findViewById(R.id.stop_button)

        bindService(
            Intent(this, ScreenCaptureForegroundService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )

        this.mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        this.surfaceView.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d(TAG, "SurfaceView.surfaceCreated")
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    Log.d(TAG, "SurfaceView.surfaceChanged")
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "SurfaceView.surfaceDestroyed")
                }

            }
        )

        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                screenSharePermissionResult = result
                if (screenSharePermissionResult?.resultCode == RESULT_OK && screenSharePermissionResult?.data != null) {
                    val serviceIntent = ScreenCaptureForegroundService.createScreenCaptureIntent(
                        context = this,
                        surface = surfaceView.holder.surface,
                        resultCode = screenSharePermissionResult!!.resultCode,
                        resultData = screenSharePermissionResult!!.data!!
                    )
                    startForegroundService(serviceIntent)
                }
            }

        stopButton.isEnabled = false
        startButton.isEnabled = true
        startButton.setOnClickListener {
            launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
            startButton.isEnabled = false
            stopButton.isEnabled = true
        }

        stopButton.setOnClickListener {
            screenCaptureService?.stopScreenShare()
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)

        super.onDestroy()
    }
}