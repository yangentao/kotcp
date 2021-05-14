package dev.entao.tcp

import java.nio.channels.SelectionKey

//
//
fun testTcpClient() {
    val callback = object : TcpClientCallback {
        override fun onTcpRead(key: SelectionKey, data: ByteArray) {
            println(data.strUTF8)
        }

        override fun onTcpConnected(key: SelectionKey) {
            println("Connected")
            key.writeFrame("Yang")
        }

        override fun onTcpConnectFailed(key: SelectionKey) {
            println("Connect Failed")
        }

        override fun onTcpLoopEnd() {
            println("Finished")
        }
    }

    val a = TcpClient(LineFrame(), callback)
    a.start("localhost", 9000)
    println("client Started")
    Thread.sleep(30_000)
    a.stop()
    println("client END")
}

fun main() {

    val n = Runtime.getRuntime().availableProcessors()
    println(n)
    testTcpClient()
}