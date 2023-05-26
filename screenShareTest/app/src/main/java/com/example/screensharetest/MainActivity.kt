package com.example.screensharetest

import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.surfaceView = findViewById(R.id.surface_view)
        this.startButton = findViewById(R.id.start_button)
        this.stopButton = findViewById(R.id.stop_button)

        this.mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                screenSharePermissionResult = result
                if (screenSharePermissionResult?.resultCode == RESULT_OK && screenSharePermissionResult?.data != null) {
                    val serviceIntent = ScreenCaptureForegroundService.createStartRecordingIntent(
                        context = this,
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
            val serviceIntent = ScreenCaptureForegroundService.createStopRecordingIntent(this)
            startService(serviceIntent)

            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }
}