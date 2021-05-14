package dev.entao.tcp

//import dev.entao.base.SEC
//import dev.entao.kava.log.logd
import java.nio.channels.SelectionKey

//
//
fun main() {
    val callback = object : TcpServerCallback {
        override fun onClientRead(key: SelectionKey, data: ByteArray) {
            println(data.strUTF8)
            key.writeFrame("Hello " + data.strUTF8)
        }

        override fun onClientAdded(key: SelectionKey) {
            println("onAdded")
        }

        override fun onClientRemoved(key: SelectionKey) {
            println("onRemoved")
        }
    }
    val a = TcpServer(LineFrame(), callback)
    a.start(9000)
    println("Main Started")
    Thread.sleep(200_000)
    a.stop()
    println("Main END")
}