package com.example.screensharetest

import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.screensharetest.service.screenrecord.ScreenCaptureForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var screenSharePermissionResult: ActivityResult? = null

    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            startRecording(launcher)
        }

        stopButton.setOnClickListener {
            stopRecording()
        }
    }

    private fun startRecording(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
        isRecording = true

        syncButtonsUI()
    }

    private fun stopRecording() {
        val serviceIntent = ScreenCaptureForegroundService.createStopRecordingIntent(this)
        startService(serviceIntent)

        isRecording = false
        syncButtonsUI()
    }

    override fun onDestroy() {
        if (isRecording) {
            stopRecording()

            Toast.makeText(this, "Recording was saved...", Toast.LENGTH_SHORT).show()
        }
        super.onDestroy()
    }

    private fun syncButtonsUI() {
        startButton.isEnabled = isRecording.not()
        stopButton.isEnabled = isRecording
    }
}