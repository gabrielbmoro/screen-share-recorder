package com.example.screenrecorder.service

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.DisplayMetrics
import android.view.Surface
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class RecordHandler(
    looper: Looper,
    private val mediaProjection: MediaProjection,
    private val outputFile: String,
    private val displayMetrics: DisplayMetrics,
    private val callback: Callback
) : Handler(looper) {

    private val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val mediaFormat = MediaFormat.createVideoFormat(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        displayMetrics.widthPixels,
        displayMetrics.heightPixels
    ).apply {
        setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    private lateinit var mediaMuxer: MediaMuxer

    private var surface: Surface? = null

    private var virtualDisplay: VirtualDisplay? = null

    private var videoTrackIndex: Int? = null
    private val isRecording = AtomicBoolean(false)

    private fun setupMediaCodec() {
        // Configure the MediaCodec encoder with the MediaFormat
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        Timber.d("Media codec -> configured")

        // Set callbacks
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                Timber.d("Media codec -> Callback -> onInputBufferAvailable")
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                Timber.d("Media codec -> Callback -> onOutputBufferAvailable")
                // Output buffer is available for processing
                if (videoTrackIndex != null) {
                    codec.getOutputBuffer(index)?.let { outputBuffer ->
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                            // Write the encoded data to the MediaMuxer if it's initialized and the buffer is valid
                            Timber.d("write the encoded data")
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            mediaMuxer.writeSampleData(videoTrackIndex!!, outputBuffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)

                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Timber.d("Media codec -> Callback -> onOutputBufferAvailable -> end of stream")

                            mediaCodec.stop()
                            mediaCodec.release()
                            mediaMuxer.stop()
                            mediaMuxer.release()

                            isRecording.set(false)
                            callback.onRecordStopped()
                        }
                    }
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Timber.d("Media codec -> Callback -> onError $e")
                isRecording.set(false)
                callback.onRecordError(e)
                // Error occurred during encoding, handle accordingly
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Timber.d("Media codec -> Callback -> onOutputFormatChanged $format")

                videoTrackIndex = mediaMuxer.addTrack(format)
                mediaMuxer.start()
                isRecording.set(true)
                callback.onRecordStarted()
            }
        }, this)

        mediaMuxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoTrackIndex = null

        // Create a surface from the MediaCodec encoder
        surface = mediaCodec.createInputSurface()
        Timber.d("Media codec -> surface created")
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            START_RECORD_MESSAGE -> handleStartRecordMessage()
            STOP_RECORD_MESSAGE -> handleStopRecordMessage()
        }
    }

    private fun handleStartRecordMessage() {
        setupMediaCodec()

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
                    mediaCodec.start()
                }

                override fun onStopped() {
                    Timber.d("Virtual display -> onStopped")
                }
            },
            this
        )
    }

    private fun handleStopRecordMessage() {
        mediaProjection.stop()
        virtualDisplay?.release()

        virtualDisplay = null
        surface = null

        try {
            if (isRecording.get()) {
                mediaCodec.signalEndOfInputStream()
                Timber.d("Media codec -> signal of end of input stream sent")
            } else {
                throw IllegalStateException("Record should stop now")
            }
        } catch (exception: Exception) {
            callback.onRecordError(exception)
        }
    }

    interface Callback {
        fun onRecordError(exception: Exception)
        fun onRecordStopped()

        fun onRecordStarted()
    }

    companion object {
        private const val START_RECORD_MESSAGE = 0
        private const val STOP_RECORD_MESSAGE = 1

        fun startMessage() = Message().apply {
            what = START_RECORD_MESSAGE
        }

        fun stopMessage() = Message().apply {
            what = STOP_RECORD_MESSAGE
        }
    }
}