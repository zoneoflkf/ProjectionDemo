package com.lkf.remotecontrol.net.client

import android.util.Log
import com.lkf.remotecontrol.net.constants.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

object ProjectionClientManager {
    private const val TAG = "ProjectionClientManager"
    val client: ProjectionClient
        get() {
            return realClient
        }

    private val realClient by lazy { return@lazy InnerProjectionClient() }
    private val scope = GlobalScope
    private const val CONNECT_TIMEOUT: Long = 10_000
    private const val DETECT_INTERVAL: Long = 5000
    private const val RECONNECT_INTERVAL: Long = 3000

    val clientStateListeners = HashSet<ClientStateListener>()

    init {
        realClient.connect()
        startClientDamon()
    }

    private fun startClientDamon() {
        scope.launch(Dispatchers.Main) {
            delay(CONNECT_TIMEOUT)

            while (true) {
                if (realClient.isOpenEx()) {
                    //Log.d(TAG, "startClientDamon: Service connecting")
                    delay(DETECT_INTERVAL)
                    continue
                }
                withContext(Dispatchers.Default) {
                    Log.w(TAG, "startClientDamon: Service not connect, retrying...")
                    runCatching {
                        realClient.reconnectWithCurUri()
                    }.onSuccess {
                        if (it) Log.d(TAG, "startClientDamon: connect OK !!!")
                        else delay(RECONNECT_INTERVAL)
                    }.onFailure { delay(RECONNECT_INTERVAL) }
                }
            }
        }
    }

    private fun getUri(): URI {
        return URI.create("ws://${ServerConfig.IP}:${ServerConfig.PORT}")
    }

    private class InnerProjectionClient : ProjectionClient(getUri(), CONNECT_TIMEOUT.toInt()) {
        private var isClose = false
        private var isError = false

        override fun onOpen(handshakedata: ServerHandshake?) {
            isClose = false
            isError = false
            super.onOpen(handshakedata)
            clientStateListeners.forEach { it.onOpen(this) }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            isClose = true
            super.onClose(code, reason, remote)
            clientStateListeners.forEach { it.onClose(this) }
        }

        override fun onError(ex: Exception?) {
            isError = true
            super.onError(ex)
            clientStateListeners.forEach { it.onError(this, ex) }
        }

        fun reconnectWithCurUri(): Boolean {
            uri = getUri()
            return reconnectBlocking()
        }

        fun isOpenEx(): Boolean {
            return isOpen && !isClose && !isError
        }
    }
}