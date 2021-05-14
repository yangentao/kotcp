package dev.entao.tcp

//import dev.entao.base.SEC
//import dev.entao.kava.log.logd
import java.nio.channels.SelectionKey
//
//
//fun main() {
//	val callback = object : TcpServerCallback {
//		override fun onClientRead(key: SelectionKey, data: ByteArray) {
//			logd(data.strUTF8)
//			key.writeFrame("Hello " + data.strUTF8)
//		}
//
//		override fun onClientAdded(key: SelectionKey) {
//			logd("onAdded")
//		}
//
//		override fun onClientRemoved(key: SelectionKey) {
//			logd("onRemoved")
//		}
//	}
//	val a = TcpServer(LineFrame(), callback)
//	a.start(9000)
//	logd("Main Started")
//	Thread.sleep(20.SEC)
//	a.stop()
//	logd("Main END")
//}