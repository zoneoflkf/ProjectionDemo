package com.lkf.remotecontrol

import com.lkf.remotecontrol.net.constants.ServerConfig
import com.lkf.remotecontrol.net.server.ProjectionServer
import org.java_websocket.WebSocket
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    /*@Test
    fun serverTest() {
        val latch = CountDownLatch(1)
        startServer()
        latch.await()
    }*/

    /*private fun startServer() {
        val address = InetSocketAddress(ServerConfig.PORT)
        object : ProjectionServer(address) {
            override fun onError(conn: WebSocket?, ex: Exception?) {
                super.onError(conn, ex)
                //服务意外挂掉,尝试重启
                Thread.sleep(1000)
                //ServerConfig.PORT++ //失败尝试换个端口,避免上次端口被占用
                startServer()
            }
        }.also { it.start() }

        *//*val address = InetSocketAddress(ServerConfig.PORT)
        ProjectionServer(address).also(ProjectionServer::start)*//*
    }*/
}