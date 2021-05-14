@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package dev.entao.tcp

import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel

interface TcpServerCallback {
	fun onClientAdded(key: SelectionKey) {}
	fun onClientRemoved(key: SelectionKey) {}
	fun onClientReadIdle(key: SelectionKey) {}
	fun onClientRead(key: SelectionKey, data: ByteArray) {}

	fun onServerLoopEnd() {}
	fun onServerStoped() {}
}

class TcpServer(val bufferFrame: BufferFrame, val callback: TcpServerCallback) {


	private val listenCallback: TcpLoopCallback = object : TcpLoopCallback {
		override fun onKeyAcceptable(key: SelectionKey) {
			acceptClient(key)
		}

		override fun onLoopFinish() {
			callback.onServerLoopEnd()
		}
	}

	private val workCallback: TcpLoopCallback = object : TcpLoopCallback {
		override fun onKeyAdded(key: SelectionKey) {
			callback.onClientAdded(key)
		}

		override fun onKeyRemoved(key: SelectionKey) {
			callback.onClientRemoved(key)
		}

		override fun onKeyReadIdle(key: SelectionKey) {
			callback.onClientReadIdle(key)
		}

		override fun onReadFrame(key: SelectionKey, data: ByteArray) {
			callback.onClientRead(key, data)
		}
	}
	private var listenLoop: TcpLoop = TcpLoop(bufferFrame, listenCallback)
	val listenKey: SelectionKey? get() = listenLoop.channels.firstOrNull()
	val workers = ArrayList<TcpLoop>()
	var maxWorkPoolCount: Int = Runtime.getRuntime().availableProcessors()


	val isOpen: Boolean
		get() = this.listenLoop.isOpen


	private fun acceptClient(key: SelectionKey) {
		val svr = key.channel() as ServerSocketChannel
		val client = svr.accept()//ex
		val w = workers.minByOrNull { it.size }
		w?.add(client, SelectionKey.OP_READ)
	}


	@Synchronized
	fun start(port: Int) {
		assert(maxWorkPoolCount > 0)
		assert(bufferFrame.maxFrameLength > 0)
		if (isOpen) {
			throw IllegalStateException("已存在是start状态")
		}
		val ch = ServerSocketChannel.open()
		ch.configureBlocking(false)
		try {
			ch.socket().bind(InetSocketAddress(port), 128)
		} catch (ex: Exception) {
			ex.printStackTrace()
			ch.close()
			throw ex
		}
		for (i in 0 until maxWorkPoolCount) {
			workers += TcpLoop(bufferFrame, workCallback)
		}
		for (w in workers) {
			w.startLoop()
		}
		listenLoop.startLoop()
		listenLoop.add(ch, SelectionKey.OP_ACCEPT)
	}

	@Synchronized
	fun stop() {
		listenLoop.stopLoop()
		for (w in workers) {
			w.stopLoop()
		}
		workers.clear()
		callback.onServerStoped()
	}

}
