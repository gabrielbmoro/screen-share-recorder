package com.example.screensharetest.service.screenrecord

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

class RecordHandler(
    looper: Looper,
    private val mediaProjection: MediaProjection,
    outputFile: String,
    private val displayMetrics: DisplayMetrics,
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
    private val mediaMuxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()

    private var surface: Surface? = null

    private var virtualDisplay: VirtualDisplay? = null

    private fun setupMediaCodec() {
        // Configure the MediaCodec encoder with the MediaFormat
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Create a surface from the MediaCodec encoder
        surface = mediaCodec.createInputSurface()

        // Start the MediaCodec encoder
        mediaCodec.start()
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            START_RECORD_MESSAGE -> {
                post {
                    setupMediaCodec()

                    virtualDisplay = mediaProjection.createVirtualDisplay(
                        "virtual display",
                        displayMetrics.widthPixels,
                        displayMetrics.heightPixels,
                        displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        surface,
                        null,
                        null
                    )
                    recordSurface()
                }
            }

            STOP_RECORD_MESSAGE -> {
                mediaCodec.signalEndOfInputStream()
                mediaProjection.stop()
                virtualDisplay?.release()
                virtualDisplay = null
                surface = null
            }
        }
    }


    private fun recordSurface() {
        var videoTrackIndex: Int? = null

        while (true) {
            val encoderStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, -1)
            when {
                // No output available yet, wait for the next buffer
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    continue
                }

                // The format of the output has changed (e.g., for the first frame), get the new format
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = mediaCodec.outputFormat
                    videoTrackIndex = mediaMuxer.addTrack(newFormat)
                    mediaMuxer.start()
                }

                // Encode and write the buffer to the MediaMuxer
                (encoderStatus >= 0) -> {
                    mediaCodec.getOutputBuffer(encoderStatus)?.let { encodedData ->
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer.writeSampleData(videoTrackIndex!!, encodedData, bufferInfo)
                    }

                    mediaCodec.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mediaCodec.stop()
                        mediaCodec.release()
                        mediaMuxer.stop()
                        mediaMuxer.release()
                        return
                    }
                }
            }
        }
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