package com.lkf.remotecontrol.net.constants

object CommandIds {
    //上传设备信息至服务端
    const val UploadDeviceInfo = 1

    //获取当前在线的设备
    const val GetOnlineDevices = 2

    //拉取其他设备的投屏
    const val PullProjection = 3

    //打开当前设备投屏
    const val StartProjection = 4

    //关闭当前设备投屏
    const val StopProjection = 5

    //发送触摸事件
    const val SendTouchInput = 6

    //收到触摸事件
    const val ReceiveTouchInput = 7

    //发送按键事件
    const val SendGlobalActionInput = 8

    //收到按键事件
    const val ReceiveGlobalActionInput = 9

    //退出控制端的投屏
    const val ReceiveExitPullProjection = 10
}