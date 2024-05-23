package com.lkf.remotecontrol.utils

import android.os.Bundle
import android.util.ArrayMap
import org.java_websocket.WebSocket

object StringEx {
    fun Bundle?.toKvString(): String {
        if (this == null) {
            return "null"
        }
        val map = ArrayMap<String, Any?>()
        keySet().forEach { k ->
            map[k] = get(k)
        }
        return map.toString()
    }

    fun WebSocket?.toHostString(): String {
        if (this != null) {
            remoteSocketAddress?.let { return it.toString() }
            localSocketAddress?.let { return it.toString() }
        }
        return ""
    }
}