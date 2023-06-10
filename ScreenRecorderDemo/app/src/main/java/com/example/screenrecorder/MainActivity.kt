package com.example.screenrecorder

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.screenrecorder.service.RecorderMode
import com.example.screenrecorder.service.ScreenCaptureForegroundService
import com.example.screenrecorder.service.ScreenCaptureInterface

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var screenSharePermissionResult: ActivityResult? = null

    private var isRecording = false

    private var screenCaptureContract: ScreenCaptureInterface? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ScreenCaptureForegroundService.ScreenCaptureBinder
            screenCaptureContract = binder?.contract
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenCaptureContract = null
        }

    }

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
                    screenCaptureContract?.startRecorder(
                        resultCode = screenSharePermissionResult!!.resultCode,
                        resultData = screenSharePermissionResult!!.data!!,
                        recorderMode = RecorderMode.SYNC
                    )
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

        bindService(
            Intent(this, ScreenCaptureForegroundService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    private fun startRecording(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
        isRecording = true

        syncButtonsUI()
    }

    private fun stopRecording() {
        screenCaptureContract?.stopRecorder()

        isRecording = false
        syncButtonsUI()
    }

    override fun onDestroy() {
        if (isRecording) {
            stopRecording()

            Toast.makeText(this, "Recording was saved...", Toast.LENGTH_SHORT).show()
        }

        unbindService(serviceConnection)

        super.onDestroy()
    }

    private fun syncButtonsUI() {
        startButton.isEnabled = isRecording.not()
        stopButton.isEnabled = isRecording
    }
}