package com.lkf.remotecontrol

import android.app.Application
import android.content.Intent
import com.lkf.remotecontrol.constants.ProjectionAppConfigs.IS_CONTROLLED

class ProjectionApp : Application() {
    companion object {
        //private const val START_TEST_SERVER = false

        lateinit var instance: Application
    }

    //private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        /*if (START_TEST_SERVER) {
            testStartServer()
        }*/

        if (IS_CONTROLLED) {
            startService(Intent(this, ProjectionService::class.java))
            KeepAliveJobService.keepAlive(this)
        }
    }

    /*private fun testStartServer() {
        Log.d("ProjectionApp", "testStartServer -> serverIp:${getIpAddress()}")
        val address = InetSocketAddress(ServerConfig.PORT)
        val server = object : ProjectionServer(address) {
            override fun onError(conn: WebSocket?, ex: Exception?) {
                super.onError(conn, ex)
                //服务意外挂掉,尝试重启
                coroutineScope.launch {
                    delay(3000)
                    //ServerConfig.PORT++ //失败尝试换个端口,避免上次端口被占用
                    testStartServer()
                }
            }
        }
        server.start()
    }*/
}