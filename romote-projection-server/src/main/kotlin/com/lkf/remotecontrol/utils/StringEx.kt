package com.lkf.remotecontrol.utils

import org.java_websocket.WebSocket

object StringEx {
    fun WebSocket?.toHostString(): String {
        if (this != null) {
            remoteSocketAddress?.let { return it.toString() }
            localSocketAddress?.let { return it.toString() }
        }
        return ""
    }
}