@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.entao.tcp

import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

interface TcpClientCallback {
	fun onTcpConnected(key: SelectionKey) {}
	fun onTcpConnectFailed(key: SelectionKey) {}
	fun onTcpReadIdle(key: SelectionKey) {}
	fun onTcpRead(key: SelectionKey, data: ByteArray) {}
	fun onTcpLoopEnd() {}
}

class TcpClient(bufferFrame: BufferFrame, val callback: TcpClientCallback) : TcpLoopCallback {
	private val clientLoop = TcpLoop(bufferFrame, this)
	val selectionKey: SelectionKey? get() = clientLoop.channels.firstOrNull()


	override fun onKeyConnectFailed(key: SelectionKey) {
		callback.onTcpConnectFailed(key)
	}

	override fun onKeyConnected(key: SelectionKey) {
		callback.onTcpConnected(key)
	}

	override fun onKeyReadIdle(key: SelectionKey) {
		callback.onTcpReadIdle(key)
	}

	override fun onReadFrame(key: SelectionKey, data: ByteArray) {
		callback.onTcpRead(key, data)
	}

	override fun onLoopFinish() {
		callback.onTcpLoopEnd()
	}

	override fun onKeyRemoved(key: SelectionKey) {
		val sel = key.selector()
		if (sel.isOpen) {
			sel.close()
		}
	}


	@Synchronized
	fun start(host: String, port: Int) {
		if (isOpen) {
			throw IllegalStateException("已存在是start状态")
		}
		clientLoop.startLoop()

		val inet = InetSocketAddress(host, port)
		val ch = SocketChannel.open()
		ch.configureBlocking(false)
		ch.connect(inet)
		clientLoop.add(ch, SelectionKey.OP_CONNECT)
	}

	@Synchronized
	fun stop() {
		clientLoop.stopLoop()
	}

	val isOpen: Boolean
		get() = this.clientLoop.isOpen

	val isActive: Boolean
		get() {
			val key = this.selectionKey ?: return false
			if (!isOpen || !key.isValid) {
				return false
			}
			val ch = key.channel() as SocketChannel
			return ch.isOpen && ch.isConnected
		}
}