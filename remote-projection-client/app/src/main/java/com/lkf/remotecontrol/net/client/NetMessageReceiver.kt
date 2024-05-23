package com.lkf.remotecontrol.net.client

import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.net.models.OnlineDevice
import com.lkf.remotecontrol.net.models.ProjectionRequest
import java.nio.ByteBuffer

interface NetMessageReceiver {
    //在线设备列表
    fun onDeviceListMessage(devices: List<OnlineDevice>)

    //投屏推流数据
    fun onProjectionStream(bytes: ByteArray)

    //开启投屏
    fun onStartProjection(req: ProjectionRequest)

    //关闭投屏
    fun onStopProjection()

    //收到远端的触屏事件
    fun onReceiveTouchInput(touchInput: TouchInput)

    //收到远端的按键事件
    fun onReceiveGlobalAction(globalActionInput: GlobalActionInput)

    //收到退出控制端投屏的命令
    fun onReceiveExitPullProjection()
}