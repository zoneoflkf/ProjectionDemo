package com.lkf.remotecontrol.net.server

import com.lkf.remotecontrol.models.BytesMessageProto
import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.net.constants.CommandIds.GetOnlineDevices
import com.lkf.remotecontrol.net.constants.CommandIds.PullProjection
import com.lkf.remotecontrol.net.constants.CommandIds.ReceiveExitPullProjection
import com.lkf.remotecontrol.net.constants.CommandIds.ReceiveGlobalActionInput
import com.lkf.remotecontrol.net.constants.CommandIds.ReceiveTouchInput
import com.lkf.remotecontrol.net.constants.CommandIds.SendGlobalActionInput
import com.lkf.remotecontrol.net.constants.CommandIds.SendTouchInput
import com.lkf.remotecontrol.net.constants.CommandIds.StartProjection
import com.lkf.remotecontrol.net.constants.CommandIds.StopProjection
import com.lkf.remotecontrol.net.constants.CommandIds.UploadDeviceInfo
import com.lkf.remotecontrol.net.models.NetMessage
import com.lkf.remotecontrol.net.models.OnlineDevice
import com.lkf.remotecontrol.net.models.ProjectionRequest
import com.lkf.remotecontrol.utils.GsonHelper.GSON
import com.lkf.remotecontrol.utils.StringEx.toHostString
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

open class ProjectionServer(address: InetSocketAddress?) : WebSocketServer(address) {
    companion object {
        private const val SHOW_DEBUG_LOG = false
    }

    private val onlineDevices = HashMap<WebSocket, OnlineDevice>()
    private val projectionRelation = HashMap<WebSocket, WebSocket>()
    private val deviceIdConnMap = HashMap<Long, WebSocket>()

    override fun onStart() {
        logI("onStart -> $address")
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        logI("onOpen -> conn:${conn.toHostString()}")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        logD("onMessage -> conn:${conn.toHostString()} | msg:$message")
        if (conn == null) {
            return
        }
        runCatching {
            val reqMsg = GSON.fromJson(message, NetMessage::class.java)
            when (reqMsg.cmdId) {
                UploadDeviceInfo -> handleDeviceOnline(conn, reqMsg)
                GetOnlineDevices -> handleGetOnlineDevices(conn, reqMsg)
                PullProjection -> handlePullProjection(conn, reqMsg)
                StopProjection -> handleStopProjection(conn, reqMsg)
                SendTouchInput -> handleSendTouchInput(conn, reqMsg)
                SendGlobalActionInput -> handleGlobalActionInput(conn, reqMsg)
            }
        }.onFailure {
            logE("onMessage: Failure -> conn:[${conn.toHostString()} | msg:$message", it)
        }
    }

    private fun handleDeviceOnline(conn: WebSocket, reqMsg: NetMessage) {
        val onlineDevice = GSON.fromJson(reqMsg.content, OnlineDevice::class.java)
        onlineDevice.deviceId = conn.toHostString().hashCode().toLong() //由服务器为设备分配一个id
        onlineDevices[conn] = onlineDevice
        deviceIdConnMap[onlineDevice.deviceId] = conn
        logI("handleDeviceOnline -> conn:${conn.toHostString()} | device:${GSON.toJson(onlineDevice)}")
    }

    private fun handleGetOnlineDevices(conn: WebSocket, reqMsg: NetMessage) {
        val deviceList = ArrayList<OnlineDevice>()
        onlineDevices.forEach { (sock, device) ->
            if (conn != sock) {
                deviceList.add(device)
            }
        }
        val respMsg = GSON.toJson(NetMessage(reqMsg.cmdId, GSON.toJson(deviceList)))
        conn.send(respMsg)
        logI("handleGetOnlineDevices -> conn:${conn.toHostString()} | resp:$respMsg")
    }

    //处理客户端发来的投屏请求,这里只给目标设备发送投屏命令,未对请求设备作响应
    private fun handlePullProjection(conn: WebSocket, reqMsg: NetMessage) {
        val projectionReq = GSON.fromJson(reqMsg.content, ProjectionRequest::class.java)
        val tarEntry = findEntryByDeviceId(projectionReq.deviceId)
        if (tarEntry == null) {
            logW("handlePullProjection: 找不到目标设备 -> conn:${conn.toHostString()} | tarDeviceId:${projectionReq.deviceId}")
            return
        }

        val fromDevice = onlineDevices[conn]
        if (fromDevice == null) {
            logW("handlePullProjection: 设备已离线")
            return
        }

        //向目标客户端发送StartProjection指令
        val targetConn = tarEntry.first
        val targetDevice = tarEntry.second
        val msgJson = GSON.toJson(NetMessage(StartProjection, GSON.toJson(ProjectionRequest(fromDevice.deviceId))))
        targetConn.send(msgJson)

        projectionRelation[conn] = targetConn

        logI("handlePullProjection -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
    }

    private fun handleStopProjection(conn: WebSocket, reqMsg: NetMessage) {
        projectionRelation.remove(conn)

        val projectionReq = GSON.fromJson(reqMsg.content, ProjectionRequest::class.java)
        val tarEntry = findEntryByDeviceId(projectionReq.deviceId)
        if (tarEntry == null) {
            logW("handleStopProjection: 找不到目标设备 -> conn:${conn.toHostString()} | tarDeviceId:${projectionReq.deviceId}")
            return
        }
        val targetConn = tarEntry.first
        val targetDevice = tarEntry.second
        sendStopProjectionCmd(targetConn) //向目标客户端发送StopProjection指令
        logI("handleStopProjection -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
    }

    private fun sendStopProjectionCmd(conn: WebSocket) {
        val msgJson = GSON.toJson(NetMessage(StopProjection, ""))
        conn.send(msgJson)
    }

    private fun handleSendTouchInput(conn: WebSocket, reqMsg: NetMessage) {
        val input: TouchInput = GSON.fromJson(reqMsg.content, TouchInput::class.java)
        val tarEntry = findEntryByDeviceId(input.deviceId)
        if (tarEntry == null) {
            logW("handleSendTouchInput: 找不到目标设备 -> conn:${conn.toHostString()} | tarDeviceId:${input.deviceId}")
            return
        }
        //向目标客户端发送ReceiveTouchInput指令
        val msgJson = GSON.toJson(NetMessage(ReceiveTouchInput, reqMsg.content))
        val targetConn = tarEntry.first
        val targetDevice = tarEntry.second
        targetConn.send(msgJson)
        logD("handleSendTouchInput -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
    }

    private fun handleGlobalActionInput(conn: WebSocket, reqMsg: NetMessage) {
        val input: GlobalActionInput = GSON.fromJson(reqMsg.content, GlobalActionInput::class.java)
        val tarEntry = findEntryByDeviceId(input.deviceId)
        if (tarEntry == null) {
            logW("handleGlobalActionInput: 找不到目标设备 -> conn:${conn.toHostString()} | tarDeviceId:${input.deviceId}")
            return
        }
        //向目标客户端发送ReceiveGlobalActionInput指令
        val msgJson = GSON.toJson(NetMessage(ReceiveGlobalActionInput, reqMsg.content))
        val targetConn = tarEntry.first
        val targetDevice = tarEntry.second
        targetConn.send(msgJson)
        logI("handleGlobalActionInput -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
    }

    private fun findEntryByDeviceId(deviceId: Long): Pair<WebSocket, OnlineDevice>? {
        val conn = deviceIdConnMap[deviceId] ?: return null
        val device = onlineDevices[conn] ?: return null
        return Pair(conn, device)
    }

    override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
        if (conn == null) {
            return
        }
        runCatching {
            val bytesMessage = BytesMessageProto.BytesMessage.parseFrom(message)
            logD("onMessage: type:${bytesMessage.type}")

            when (bytesMessage.type) {
                BytesMessageProto.Type.VIDEO_STREAM -> {
                    val videoStream = BytesMessageProto.VideoStream.parseFrom(bytesMessage.data)
                    val entry = findEntryByDeviceId(videoStream.deviceId)
                    if (entry != null) {
                        entry.first.send(message)
                        logD("onMessage: 推流 -> conn:[${conn.toHostString()} => ${entry.first.toHostString()}] | device:[${onlineDevices[conn]?.deviceId} => ${videoStream.deviceId}]")
                    } else {
                        sendStopProjectionCmd(conn)
                        logW("onMessage: 推流,控制端已离线 -> conn:{${conn.toHostString()}}")
                    }
                }

                BytesMessageProto.Type.TOUCH_INPUT -> {
                    val touchInput = BytesMessageProto.TouchInputs.parseFrom(bytesMessage.data)
                    val entry = findEntryByDeviceId(touchInput.deviceId)
                    if (entry != null) {
                        entry.first.send(message)
                    } else {
                        //受控端离线,通知控制端退出
                        sendExitPullProjectionCmd(conn)
                        logW("onMessage: TOUCH_INPUT 受控端已离线 -> conn:{${conn.toHostString()}}")
                    }
                }

                else -> logW("onMessage: 未知消息类型 -> ${bytesMessage.type}")
            }
        }.onFailure {
            logE("onMessage: Failure -> conn:[${conn.toHostString()} | msg:$message", it)
        }
    }

    private fun sendExitPullProjectionCmd(conn: WebSocket) {
        conn.send(GSON.toJson(NetMessage(ReceiveExitPullProjection, "")))
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        logI("onClose -> conn:${conn.toHostString()} | code:$code | reason:$reason | remote:$remote")

        //移除在线设备
        conn?.let { onlineDevices.remove(it) }

        //移除设备id和连接
        deviceIdConnMap.iterator().let {
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.value != conn) continue
                it.remove()
            }
        }

        //通知设备离线
        val iter = projectionRelation.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            var shouldRemove = true
            if (entry.key == conn) {
                //控制端离线,停掉对应的受控端
                sendStopProjectionCmd(entry.value)
            } else if (entry.value == conn) {
                //受控端离线,停掉对应的控制端
                sendExitPullProjectionCmd(entry.key)
            } else {
                shouldRemove = false
            }
            if (shouldRemove) iter.remove()
        }
    }

    var isError = false

    override fun onError(conn: WebSocket?, ex: Exception?) {
        logE("onError -> conn:{${conn.toHostString()}}", ex)
        isError = true
    }

    /*fun sendData(data: ByteArray?) {
        for (viewer in allViewers) {
            viewer.send(data)
        }
    }*/

    private fun logD(msg: String) {
        if (SHOW_DEBUG_LOG) println("${dateStr()} -- D -- $msg")
    }

    private fun logI(msg: String) {
        println("${dateStr()} -- I -- $msg")
    }

    private fun logW(msg: String) {
        println("${dateStr()} -- W -- $msg")
    }

    private fun logE(msg: String, ex: Throwable? = null) {
        println("${dateStr()} -- I -- $msg")
        ex?.printStackTrace()
    }

    private val dateFormat = SimpleDateFormat("YYYY-MM-dd HH:mm:ss:SSS")

    private fun dateStr(): String {
        return dateFormat.format(Date())
    }
}