package com.lkf.remotecontrol.net.server

import android.util.Log
import com.lkf.remotecontrol.models.GlobalActionInput
import com.lkf.remotecontrol.models.TouchInput
import com.lkf.remotecontrol.net.constants.CommandIds.GetOnlineDevices
import com.lkf.remotecontrol.net.constants.CommandIds.PullProjection
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

open class ProjectionServer(address: InetSocketAddress?) : WebSocketServer(address) {
    companion object {
        private const val RUN_WITH_ANDROID = false
        private const val TAG = "ProjectionServer"
    }

    private val onlineDevices = HashMap<WebSocket, OnlineDevice>()

    override fun onStart() {
        logI("onStart -> $address")
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        logI("onOpen -> conn:${conn.toHostString()}")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        logI("onMessage -> conn:${conn.toHostString()} | msg:$message")
        if (conn == null) {
            return
        }
        val reqMsg = GSON.fromJson(message, NetMessage::class.java)
        when (reqMsg.cmdId) {
            UploadDeviceInfo -> handleDeviceOnline(conn, reqMsg)
            GetOnlineDevices -> handleGetOnlineDevices(conn, reqMsg)
            PullProjection -> handlePullProjection(conn, reqMsg)
            StopProjection -> handleStopProjection(conn, reqMsg)
            SendTouchInput -> handleSendTouchInput(conn, reqMsg)
            SendGlobalActionInput -> handleGlobalActionInput(conn, reqMsg)
        }
    }

    private fun handleDeviceOnline(conn: WebSocket, reqMsg: NetMessage) {
        val onlineDevice = GSON.fromJson(reqMsg.content, OnlineDevice::class.java)
        onlineDevice.deviceId = conn.toHostString().hashCode().toLong() //由服务器为设备分配一个id
        onlineDevices[conn] = onlineDevice
        logI("handleGetOnlineDevices -> conn:${conn.toHostString()} | device:${GSON.toJson(onlineDevice)}")
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
        val tarEntry = findEntryByProjectionReq(projectionReq)
        if (tarEntry == null) {
            logW("handlePullProjection: 找不到目标设备 -> conn:${conn.toHostString()} | tarDeviceId:${projectionReq.deviceId}")
            return
        }
        //向目标客户端发送StartProjection指令
        val targetConn = tarEntry.key
        val targetDevice = tarEntry.value
        val msgJson = GSON.toJson(NetMessage(StartProjection, reqMsg.content))
        targetConn.send(msgJson)
        logI("handlePullProjection -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
    }

    private fun handleStopProjection(conn: WebSocket, reqMsg: NetMessage) {
        val projectionReq = GSON.fromJson(reqMsg.content, ProjectionRequest::class.java)
        val tarEntry = findEntryByProjectionReq(projectionReq)
        if (tarEntry == null) {
            logW("handleStopProjection: 找不到目标设备 -> conn:${conn.toHostString()} | tarDeviceId:${projectionReq.deviceId}")
            return
        }
        //向目标客户端发送StopProjection指令
        val targetConn = tarEntry.key
        val targetDevice = tarEntry.value
        val msgJson = GSON.toJson(NetMessage(StopProjection, reqMsg.content))
        targetConn.send(msgJson)
        logI("handleStopProjection -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
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
        val targetConn = tarEntry.key
        val targetDevice = tarEntry.value
        targetConn.send(msgJson)
        logI("handleSendTouchInput -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
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
        val targetConn = tarEntry.key
        val targetDevice = tarEntry.value
        targetConn.send(msgJson)
        logI("handleGlobalActionInput -> conn:${conn.toHostString()} | tarId:${targetDevice.deviceId} | tarName:${targetDevice.deviceName}")
    }

    private fun findEntryByProjectionReq(req: ProjectionRequest): Map.Entry<WebSocket, OnlineDevice>? {
        return findEntryByDeviceId(req.deviceId)
    }

    private fun findEntryByDeviceId(deviceId: Long): Map.Entry<WebSocket, OnlineDevice>? {
        var tarEntry: Map.Entry<WebSocket, OnlineDevice>? = null
        for (entry in onlineDevices.entries) {
            if (entry.value.deviceId == deviceId) {
                tarEntry = entry
                break
            }
        }
        return tarEntry
    }

    override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
        //logI("onMessage: conn:${conn.toHostString()} | byteBuffer:$conn")
        onlineDevices.keys.forEach {
            if (it != conn) {
                it.send(message)
            }
        }
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        logI("onClose -> conn:${conn.toHostString()} | code:$code | reason:$reason | remote:$remote")
        conn?.let { onlineDevices.remove(it) }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        logE("onError -> conn:{${conn.toHostString()}}", ex)
        conn?.let { onlineDevices.remove(it) }
    }

    /*fun sendData(data: ByteArray?) {
        for (viewer in allViewers) {
            viewer.send(data)
        }
    }*/

    private fun logI(msg: String) {
        if (RUN_WITH_ANDROID) {
            Log.i(TAG, "onStart -> $address")
        } else {
            println("$TAG -- I -- $msg")
        }
    }

    private fun logW(msg: String) {
        if (RUN_WITH_ANDROID) {
            Log.w(TAG, "onStart -> $address")
        } else {
            println("$TAG -- W -- $msg")
        }
    }

    private fun logE(msg: String, ex: Exception?) {
        if (RUN_WITH_ANDROID) {
            Log.e(TAG, msg, ex)
        } else {
            println("$TAG -- I -- $msg")
            ex?.printStackTrace()
        }
    }
}