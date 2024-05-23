import com.lkf.remotecontrol.net.constants.ServerConfig
import com.lkf.remotecontrol.net.server.ProjectionServer
import java.net.InetSocketAddress

fun main() {
    startServer()
    while (true) {
        Thread.sleep(Long.MAX_VALUE)
    }
}

private fun startServer() {
    val address = InetSocketAddress(ServerConfig.PORT)
    ProjectionServer(address).apply { start() }
}