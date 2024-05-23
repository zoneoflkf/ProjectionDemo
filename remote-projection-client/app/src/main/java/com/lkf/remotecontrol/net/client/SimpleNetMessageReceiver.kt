package com.lkf.remotecontrol.net.client

import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.net.models.OnlineDevice
import com.lkf.remotecontrol.net.models.ProjectionRequest
import java.nio.ByteBuffer

interface SimpleNetMessageReceiver : NetMessageReceiver {
    override fun onDeviceListMessage(devices: List<OnlineDevice>) = Unit
    override fun onProjectionStream(bytes: ByteArray) = Unit
    override fun onStartProjection(req: ProjectionRequest) = Unit
    override fun onStopProjection() = Unit
    override fun onReceiveTouchInput(touchInput: TouchInput) = Unit
    override fun onReceiveGlobalAction(globalActionInput: GlobalActionInput) = Unit
    override fun onReceiveExitPullProjection() = Unit
}