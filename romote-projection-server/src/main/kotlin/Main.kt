import com.lkf.remotecontrol.net.constants.ServerConfig
import com.lkf.remotecontrol.net.server.ProjectionServer
import java.net.InetSocketAddress

fun main() {
    val updateTime = "2024-6-5 17:24:49"
    println("Server start updateTime: $updateTime")

    val address = InetSocketAddress(ServerConfig.PORT)
    var server = ProjectionServer(address).apply { start() }
    while (true) {
        runCatching {
            if (server.isError) {
                server.stop(3000, "Stop from error")
                println("Server error, restart...")
                server = ProjectionServer(address).apply { start() }
            } else {
                Thread.sleep(5000)
            }
        }
    }
}