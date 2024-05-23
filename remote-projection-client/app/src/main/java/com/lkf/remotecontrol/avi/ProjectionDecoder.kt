package com.lkf.remotecontrol.avi

import android.media.MediaCodec
import android.view.Surface
import com.lkf.remotecontrol.avi.ProjectionConfigs.MEDIACODEC_TIME_OUT
import com.lkf.remotecontrol.avi.ProjectionConfigs.PROJECTION_VIDEO_TYPE
import java.io.IOException


class ProjectionDecoder(private val surface: Surface, private val width: Int, private val height: Int) {
    private var mediaCodec: MediaCodec? = null

    fun startDecode() {
        try {
            mediaCodec?.release()
            val codec = MediaCodec.createDecoderByType(PROJECTION_VIDEO_TYPE)
            codec.configure(ProjectionConfigs.getMediaFormat(width, height), surface, null, 0)
            codec.start()
            mediaCodec = codec
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun decodeData(data: ByteArray) {
        val mediaCodec = mediaCodec ?: return

        val inputBufIndex = mediaCodec.dequeueInputBuffer(MEDIACODEC_TIME_OUT)
        if (inputBufIndex < 0) return
        val inputBuf = mediaCodec.getInputBuffer(inputBufIndex) ?: return
        inputBuf.apply {
            clear()
            put(data)
        }
        mediaCodec.queueInputBuffer(inputBufIndex, 0, data.size, System.currentTimeMillis(), 0)

        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, MEDIACODEC_TIME_OUT)
            if (outputBufIndex < 0) break
            mediaCodec.releaseOutputBuffer(outputBufIndex, true)
        }
    }

    fun stopDecode() {
        mediaCodec?.release()
        mediaCodec = null
    }
}