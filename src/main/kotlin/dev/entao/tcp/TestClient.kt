package dev.entao.tcp

import java.nio.channels.SelectionKey
//
//
//fun testTcpClient() {
//	val callback = object : TcpClientCallback {
//		override fun onTcpRead(key: SelectionKey, data: ByteArray) {
//			logd(data.strUTF8)
//		}
//
//		override fun onTcpConnected(key: SelectionKey) {
//			logd("Connected")
//			key.writeFrame("Yang")
//		}
//
//		override fun onTcpConnectFailed(key: SelectionKey) {
//			logd("Connect Failed")
//		}
//
//		override fun onTcpLoopEnd() {
//			logd("Finished")
//		}
//	}
//
//	val a = TcpClient(LineFrame(), callback)
//	a.start("localhost", 9000)
//	logd("client Started")
//	Thread.sleep(30.SEC)
//	a.stop()
//	logd("client END")
//}
//
//fun main() {
//
//	val n = Runtime.getRuntime().availableProcessors()
//	println(n)
//}