package com.example.screenrecorder.service.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.view.Surface
import com.example.screenrecorder.service.recorder.RecorderSyncHandler.Companion.START_RECORD_MESSAGE
import com.example.screenrecorder.service.recorder.RecorderSyncHandler.Companion.STOP_RECORD_MESSAGE

class RecorderSyncHandler(
    looper: Looper,
    outputFile: String,
    widthPixels: Int,
    heightPixels: Int
) : Handler(looper) {
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var bufferInfo: MediaCodec.BufferInfo? = null
    private var surface: Surface? = null

    init {
        // Create media encoder
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

        // Configure the media format for video encoding
        val mediaFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            widthPixels,
            heightPixels
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        // Configure the MediaCodec encoder with the Media format
        mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        surface = mediaCodec?.createInputSurface()

        // Create a mediaMuxer to write the encoded data
        mediaMuxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    fun getSurface(): Surface? = surface

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            START_RECORD_MESSAGE -> {
                mediaCodec!!.start()
                post {
                    record()
                }
            }

            STOP_RECORD_MESSAGE -> {
                stopRecord()
            }
        }
    }

    private fun record() {
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
                        mediaMuxer!!.writeSampleData(
                            videoTrackIndex!!,
                            encodedData,
                            bufferInfo!!
                        )
                    }

                    mediaCodec!!.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mediaCodec!!.stop()
                        mediaCodec!!.release()
                        mediaMuxer!!.stop()
                        mediaMuxer!!.release()

                        surface = null
                        mediaCodec = null
                        mediaMuxer = null
                        bufferInfo = null
                        return
                    }
                }
            }
        }
    }

    private fun stopRecord() {
        mediaCodec?.signalEndOfInputStream()
    }

    companion object {
        const val START_RECORD_MESSAGE = 0
        const val STOP_RECORD_MESSAGE = 1
    }
}


class MediaRecorderSyncStrategy : MediaRecorderStrategy {
    private var handlerThread: HandlerThread? = null
    private var recorderSyncHandler: RecorderSyncHandler? = null

    override fun setup(outputFile: String, widthPixels: Int, heightPixels: Int) {
        handlerThread = HandlerThread("video_recorder_thread")
        handlerThread!!.start()

        recorderSyncHandler = RecorderSyncHandler(
            looper = handlerThread!!.looper,
            outputFile = outputFile,
            widthPixels = widthPixels,
            heightPixels = heightPixels
        )
    }

    override fun start() {
        recorderSyncHandler?.handleMessage(Message().apply { what = START_RECORD_MESSAGE })
    }

    override fun stop() {
        recorderSyncHandler?.handleMessage(Message().apply { what = STOP_RECORD_MESSAGE })
        handlerThread?.quitSafely()
    }

    override fun release() {
        recorderSyncHandler = null
        handlerThread = null
    }

    override fun getSurface(): Surface? = recorderSyncHandler?.getSurface()
}