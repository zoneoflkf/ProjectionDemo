package com.lkf.remotecontrol.avi

import android.media.MediaCodecInfo
import android.media.MediaFormat

object ProjectionConfigs {
    //限制投屏最大分辨率
    const val PROJECTION_SIZE_LIMIT = 720

    //视频编码类型
    const val PROJECTION_VIDEO_TYPE = MediaFormat.MIMETYPE_VIDEO_HEVC

    //MediaCodec编解码的超时
    const val MEDIACODEC_TIME_OUT: Long = 10_000

    //投屏帧率
    private const val PROJECTION_FPS = 15

    //I帧的频率
    private const val I_FRAME_INTERVAL = 2

    fun getMediaFormat(width: Int, height: Int): MediaFormat {
        return MediaFormat.createVideoFormat(PROJECTION_VIDEO_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, width * height) //比特率（比特/秒）
            setInteger(MediaFormat.KEY_FRAME_RATE, PROJECTION_FPS) //帧率
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL) //I帧的频率
        }
    }
}