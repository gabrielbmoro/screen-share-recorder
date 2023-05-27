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
    private val outputFile: String,
    private val displayMetrics: DisplayMetrics,
) : Handler(looper) {

    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var bufferInfo: MediaCodec.BufferInfo? = null

    private var surface: Surface? = null

    private var virtualDisplay: VirtualDisplay? = null

    private fun setupMediaCodec() {
        // Create a MediaCodec encoder
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

        // Configure the MediaFormat for video encoding
        val mediaFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        // Configure the MediaCodec encoder with the MediaFormat
        mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Create a mediaMuxer to write the encoded data
        mediaMuxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // Create a surface from the MediaCodec encoder
        surface = mediaCodec!!.createInputSurface()

        // Start the MediaCodec encoder
        mediaCodec!!.start()
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
                mediaCodec!!.signalEndOfInputStream()
                mediaProjection.stop()
                virtualDisplay?.release()
            }
        }
    }


    private fun recordSurface() {
        var videoTrackIndex: Int? = null

        bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val encoderStatus = mediaCodec?.dequeueOutputBuffer(bufferInfo!!, -1)
            when {
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet, wait for the next buffer
                    continue
                }

                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // The format of the output has changed (e.g., for the first frame), get the new format
                    val newFormat = mediaCodec!!.outputFormat
                    videoTrackIndex = mediaMuxer!!.addTrack(newFormat)
                    mediaMuxer!!.start()
                }

                (encoderStatus != null && encoderStatus >= 0) -> {
                    // Encode and write the buffer to the MediaMuxer
                    mediaCodec!!.getOutputBuffer(encoderStatus)?.let { encodedData ->
                        encodedData.position(bufferInfo!!.offset)
                        encodedData.limit(bufferInfo!!.offset + bufferInfo!!.size)
                        mediaMuxer!!.writeSampleData(videoTrackIndex!!, encodedData, bufferInfo!!)
                    }

                    mediaCodec!!.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mediaCodec!!.stop()
                        mediaCodec!!.release()
                        mediaMuxer!!.stop()
                        mediaMuxer!!.release()
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