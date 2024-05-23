package com.lkf.remotecontrol.avi

import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log
import com.lkf.remotecontrol.avi.ProjectionConfigs.MEDIACODEC_TIME_OUT
import com.lkf.remotecontrol.avi.ProjectionConfigs.PROJECTION_VIDEO_TYPE
import java.io.IOException

class ProjectionEncoder(
    private val mediaProjection: MediaProjection, width: Int, height: Int,
    private val onEncoded: (ByteArray) -> Unit,
) {
    companion object {
        private const val TAG = "ProjectionEncoder"
    }

    private lateinit var mediaCodec: MediaCodec

    init {
        try {
            mediaCodec = MediaCodec.createEncoderByType(PROJECTION_VIDEO_TYPE)
            mediaCodec.configure(ProjectionConfigs.getMediaFormat(width, height), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = mediaCodec.createInputSurface()
            mediaProjection.createVirtualDisplay("Projection", width, height, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private var encodeThread: Thread? = null

    fun startEncoding() {
        encodeThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            try {
                mediaCodec.start()
                val bufferInfo = MediaCodec.BufferInfo()
                while (true) {
                    val outputBufIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, MEDIACODEC_TIME_OUT)
                    if (outputBufIndex < 0) continue
                    val byteBuffer = mediaCodec.getOutputBuffer(outputBufIndex) ?: continue
                    ByteArray(bufferInfo.size).let {
                        byteBuffer.get(it)
                        onEncoded(it)
                    }
                    mediaCodec.releaseOutputBuffer(outputBufIndex, false)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "startEncoding: Interrupt", e)
            }
        }.also { it.start() }
    }

    fun stopEncoding() {
        encodeThread?.interrupt()
        encodeThread = null
        mediaCodec.release()
        mediaProjection.stop()
    }
}