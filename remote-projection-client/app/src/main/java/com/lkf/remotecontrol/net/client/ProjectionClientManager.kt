package com.lkf.remotecontrol.net.client

import android.util.Log
import com.lkf.remotecontrol.net.constants.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.TimeUnit

object ProjectionClientManager {
    private const val TAG = "ProjectionClientManager"
    val client: ProjectionClient
        get() {
            return realClient
        }

    private val realClient by lazy { return@lazy InnerProjectionClient() }
    private val scope: CoroutineScope by lazy { CoroutineScope(Dispatchers.Default) }
    private const val CONNECT_TIMEOUT: Long = 10_000
    private const val DETECT_INTERVAL: Long = 5000
    private const val RECONNECT_INTERVAL: Long = 3000

    val clientStateListeners = HashSet<ClientStateListener>()

    init {
        realClient.connect()
        startClientDamon()
    }

    private fun startClientDamon() {
        scope.launch(Dispatchers.IO) {
            delay(CONNECT_TIMEOUT)
            while (true) {
                if (client.isOpen) {
//                    Log.d(TAG, "startClientDamon: 服务正常")
                    delay(DETECT_INTERVAL)
                    continue
                }
                Log.w(TAG, "startClientDamon: 服务未连接,重试...")
                runCatching {
                    realClient.reconnectWithCurUri()
                }.onSuccess {
                    if (it) Log.d(TAG, "startClientDamon: 连接成功!!!")
                    else delay(RECONNECT_INTERVAL)
                }.onFailure { delay(RECONNECT_INTERVAL) }
            }
        }
    }

    private fun getUri(): URI {
        return URI.create("ws://${ServerConfig.IP}:${ServerConfig.PORT}")
    }

    private class InnerProjectionClient : ProjectionClient(getUri(), CONNECT_TIMEOUT.toInt()) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            super.onOpen(handshakedata)
            clientStateListeners.forEach { it.onOpen(this) }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            super.onClose(code, reason, remote)
            clientStateListeners.forEach { it.onClose(this) }
        }

        override fun onError(ex: Exception?) {
            super.onError(ex)
            clientStateListeners.forEach { it.onError(this, ex) }
        }

        fun reconnectWithCurUri(): Boolean {
            uri = getUri()
            return reconnectBlocking(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
        }
    }
}