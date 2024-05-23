package com.lkf.remotecontrol.net.models

data class OnlineDevice(
    val width: Int, val height: Int,
    val deviceName: String?, val versionCode: Int, val versionName: String?
)   {
    var deviceId: Long = 0
}