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
    const val PROJECTION_FPS = 15

    // 比特率
    private const val BIT_RATE = 2_000_000

    /**
     * 固定码率（CBR）：码率恒定，适合网络条件稳定的场景。(更适合低延迟场景)
     * 可变码率（VBR）：码率根据内容复杂度动态调整，适合网络条件不稳定的场景。
     */
    private const val BITRATE_MODE = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR

    // 关键帧间隔
    private const val I_FRAME_INTERVAL = 1

    fun getMediaFormat(width: Int, height: Int): MediaFormat {
        return MediaFormat.createVideoFormat(PROJECTION_VIDEO_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, PROJECTION_FPS)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_BITRATE_MODE, BITRATE_MODE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }
    }
}