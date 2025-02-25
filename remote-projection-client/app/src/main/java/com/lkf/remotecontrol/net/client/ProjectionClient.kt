package com.lkf.remotecontrol.net.client

import android.util.Log
import androidx.collection.ArraySet
import com.lkf.remotecontrol.models.BytesMessageProto
import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.net.constants.CommandIds
import com.lkf.remotecontrol.net.models.NetMessage
import com.lkf.remotecontrol.net.models.OnlineDevice
import com.lkf.remotecontrol.net.models.ProjectionRequest
import com.lkf.remotecontrol.utils.DeviceUtil
import com.lkf.remotecontrol.utils.DeviceUtil.getScreenSize
import com.lkf.remotecontrol.utils.GsonHelper.GSON
import com.lkf.remotecontrol.utils.GsonHelper.jsonToList
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

open class ProjectionClient(serverUri: URI?, connectTimeout: Int) : WebSocketClient(
    serverUri,
    Draft_6455(),
    null,
    connectTimeout
), NetMessageReceiver, IProjectionBiz {
    companion object {
        private const val TAG = "ProjectionClient"
    }

    val messageReceivers = ArraySet<NetMessageReceiver>()

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.i(TAG, "onOpen")
        uploadOnlineDevice()
    }

    override fun onMessage(message: String?) {
        Log.i(TAG, "onMessage: $message")
        val respMsg = GSON.fromJson(message, NetMessage::class.java)
        when (respMsg.cmdId) {
            CommandIds.GetOnlineDevices -> {
                val deviceList = jsonToList(respMsg.content, Array<OnlineDevice>::class.java)
                onDeviceListMessage(deviceList)
            }

            CommandIds.StartProjection -> {
                val req = GSON.fromJson(respMsg.content, ProjectionRequest::class.java)
                onStartProjection(req)
            }

            CommandIds.StopProjection -> {
                onStopProjection()
            }

            CommandIds.ReceiveTouchInput -> {
                val touchInput = GSON.fromJson(respMsg.content, TouchInput::class.java)
                onReceiveTouchInput(touchInput)
            }

            CommandIds.ReceiveGlobalActionInput -> {
                val globalActionInput = GSON.fromJson(respMsg.content, GlobalActionInput::class.java)
                onReceiveGlobalAction(globalActionInput)
            }

            CommandIds.ReceiveExitPullProjection -> {
                onReceiveExitPullProjection()
            }
        }
    }

    override fun onMessage(bytes: ByteBuffer?) {
        if (bytes == null) {
            return
        }
        runCatching {
            val bytesMessage = BytesMessageProto.BytesMessage.parseFrom(bytes)
            when (bytesMessage.type) {
                BytesMessageProto.Type.VIDEO_STREAM -> {
                    val videoStream = BytesMessageProto.VideoStream.parseFrom(bytesMessage.data)
                    onProjectionStream(videoStream.toByteArray())
                }

                BytesMessageProto.Type.TOUCH_INPUT -> {
                    val touchInputProto = BytesMessageProto.TouchInputs.parseFrom(bytesMessage.data)
                    touchInputProto.motionEventsList.forEach {
                        onReceiveTouchInput(TouchInput(touchInputProto.deviceId, it.xAxis, it.yAxis, it.action, it.timestamp))
                    }
                }

                else -> throw Exception("未知消息类型")
            }
        }.onFailure {
            Log.e(TAG, "onMessage(ByteBuffer): 消息处理异常", it)
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.i(TAG, "onClose -> code:$code | reason:$reason | remote:$remote")
    }

    override fun onError(ex: Exception?) {
        Log.e(TAG, "onError", ex)
    }

    private fun uploadOnlineDevice() {
        val screenSize = getScreenSize()
        // 规定: 短边是宽, 长边是高
        val width = min(screenSize.x, screenSize.y)
        val height = max(screenSize.x, screenSize.y)
        val device = OnlineDevice(
            width, height,
            DeviceUtil.getDeviceName(),
            DeviceUtil.getVersionCode(),
            DeviceUtil.getVersionName()
        )
        val msg = NetMessage(CommandIds.UploadDeviceInfo, GSON.toJson(device))
        send(GSON.toJson(msg).also { json ->
            Log.i(TAG, "uploadOnlineDevice: $json")
        })
    }

    //获取当前在线设备列表
    override fun requestOnlineDevices() {
        send(GSON.toJson(NetMessage(CommandIds.GetOnlineDevices, "")).also { json ->
            Log.i(TAG, "getOnlineDevices: $json")
        })
    }

    override fun requestPullProjection(req: ProjectionRequest) {
        val reqJson = GSON.toJson(req)
        val msgJson = GSON.toJson(NetMessage(CommandIds.PullProjection, reqJson)).also {
            Log.i(TAG, "requestPullProjection: $it")
        }
        send(msgJson)
    }

    override fun requestStopPullProjection(req: ProjectionRequest) {
        val reqJson = GSON.toJson(req)
        val msgJson = GSON.toJson(NetMessage(CommandIds.StopProjection, reqJson)).also {
            Log.i(TAG, "requestStopPullProjection: $it")
        }
        send(msgJson)
    }

    override fun onDeviceListMessage(devices: List<OnlineDevice>) {
        messageReceivers.forEach { it.onDeviceListMessage(devices) }
    }

    override fun onProjectionStream(bytes: ByteArray) {
        Log.i(TAG, "onProjectionStream: $bytes")
        messageReceivers.forEach { it.onProjectionStream(bytes) }
    }

    override fun onStartProjection(req: ProjectionRequest) {
        messageReceivers.forEach { it.onStartProjection(req) }
    }

    override fun onStopProjection() {
        messageReceivers.forEach { it.onStopProjection() }
    }

    override fun onReceiveTouchInput(touchInput: TouchInput) {
        Log.i(TAG, "onReceiveTouchInput: $touchInput")
        messageReceivers.forEach { it.onReceiveTouchInput(touchInput) }
    }

    override fun onReceiveGlobalAction(globalActionInput: GlobalActionInput) {
        messageReceivers.forEach { it.onReceiveGlobalAction(globalActionInput) }
    }

    override fun onReceiveExitPullProjection() {
        messageReceivers.forEach { it.onReceiveExitPullProjection() }
    }

    override fun send(text: String?) {
        runCatching { super.send(text) }.onFailure { Log.e(TAG, "send: $text", it) }
    }

    override fun send(data: ByteArray?) {
        runCatching { super.send(data) }.onFailure { Log.e(TAG, "send: $data", it) }
    }

    override fun send(bytes: ByteBuffer?) {
        runCatching { super.send(bytes) }.onFailure { Log.e(TAG, "send: $bytes", it) }
    }

    /*@Throws(InterruptedException::class)
    fun reconnectBlocking(timeout: Long, timeUnit: TimeUnit): Boolean {
        val reset = WebSocketClient::class.java.getDeclaredMethod("reset").apply { isAccessible = true }
        reset.invoke(this)
        return connectBlocking(timeout, timeUnit)
    }*/
}