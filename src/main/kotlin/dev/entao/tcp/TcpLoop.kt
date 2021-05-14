@file:Suppress("MemberVisibilityCanBePrivate")

package dev.entao.tcp

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.spi.AbstractSelectableChannel
import java.util.*


interface TcpLoopCallback {
	fun onKeyConnected(key: SelectionKey) {}
	fun onKeyConnectFailed(key: SelectionKey) {}
	fun onKeyAcceptable(key: SelectionKey) {}

	fun onKeyAdded(key: SelectionKey) {}
	fun onKeyRemoved(key: SelectionKey) {}
	fun onKeyReadIdle(key: SelectionKey) {}
	fun onReadFrame(key: SelectionKey, data: ByteArray) {}

	fun onLoopFinish() {}
}

class TcpLoop(val bufferFrame: BufferFrame, val callback: TcpLoopCallback) {
	private var thread: Thread? = null
	private var selector: Selector? = null
	private val channelList: LinkedList<SelectionKey> = LinkedList()
	private val buf = ByteBuffer.allocate(4096)
	private var preTime: Long = 0L
	//读超时时间
	var readIdleSeconds: Int = 90


	val isOpen: Boolean get() = selector?.isOpen ?: false
	val channels: ArrayList<SelectionKey> get() = ArrayList(channelList)

	val size: Int get() = channelList.size

	fun startLoop() {
		assert(channelList.isEmpty())
		this.selector = Selector.open()
		val t = Thread(::run, "TcpWorkerLoop")
		t.isDaemon = true
		t.start()
		this.thread = t
	}

	fun stopLoop() {
		for (key in channels) {
			closeChannel(key)
		}
		if (this.selector?.isOpen == true) {
			this.selector?.selectNow()
		}
		this.selector?.close()
		this.selector = null
		if (thread?.isAlive == true) {
			thread?.join(30_000)
		}
		thread = null
	}

	fun add(channel: AbstractSelectableChannel, op: Int) {
		val sel = this.selector
		if (sel == null || !sel.isOpen) {
			channel.close()
			return
		}
		if (channel.isBlocking) {
			channel.configureBlocking(false)
		}
		val key = channel.register(sel, op)
		key.framer = bufferFrame
		synchronized(channelList) {
			channelList.add(key)
		}
		callback.onKeyAdded(key)
		selector?.wakeup()
	}

	private fun closeChannel(key: SelectionKey) {
		key.close()
		var removed: Boolean
		synchronized(channelList) {
			removed = channelList.remove(key)
		}
		if (removed) {
			try {
				callback.onKeyRemoved(key)
			} catch (ex: Exception) {
				ex.printStackTrace()
			}
		}
		key.attach(null)
	}

	private fun run() {
		try {
			val sel = this.selector ?: return
			runService(sel)
		} catch (ex: Exception) {
			ex.printStackTrace()
		}
		for (ch in channels) {
			closeChannel(ch)
		}
		callback.onLoopFinish()
	}

	private fun checkIdle() {
		val curr = System.currentTimeMillis()
		if (curr - preTime >= 5000) {
			preTime = curr
			try {
				val t = System.currentTimeMillis()
				val tm = this.readIdleSeconds * 1000L
				val ls = channels.filter { t - it.readTime > tm }
				for (k in ls) {
					callback.onKeyReadIdle(k)
				}
			} catch (ex: Exception) {
				ex.printStackTrace()
			}
		}
	}

	private fun runService(sel: Selector) {
		while (sel.isOpen) {
			checkIdle()
			try {
				if (sel.select(2000) == 0) {
					continue
				}
			} catch (ex: ClosedSelectorException) {
				return
			} catch (ioe: IOException) {
				sel.close()
				return
			}
			val ite = sel.selectedKeys().iterator()
			while (ite.hasNext()) {
				val key = ite.next()
				ite.remove()
				if (!key.isValid) {
					closeChannel(key)
					continue
				}
				try {
					if (key.isConnectable) {
						val ch = key.channel() as SocketChannel
						if (ch.isConnectionPending && ch.finishConnect()) {
							key.interestOps(SelectionKey.OP_READ)
							callback.onKeyConnected(key)
						} else {
							closeChannel(key)
							callback.onKeyConnectFailed(key)
						}
					} else if (key.isAcceptable) {
						callback.onKeyAcceptable(key)
					} else if (key.isReadable) {
						key.readTime = System.currentTimeMillis()
						if (-1 == readKey(key)) {
							closeChannel(key)
						}
					}
				} catch (ex: Exception) {
					ex.printStackTrace()
				}

			}
		}
	}


	//if -1 then closeChannel(key)
	private fun readKey(key: SelectionKey): Int {
		val ch = key.channel() as SocketChannel
		var readData: ByteArray? = null
		do {
			buf.clear()
			val nRead = try {
				ch.read(buf)
			} catch (ex: Exception) {
				ex.printStackTrace()
				-1
			}
			if (nRead == -1) {
				return -1
			}
			if (nRead == 0) {
				break
			}
			if (nRead > 0) {
				buf.flip()
				val ba = ByteArray(nRead) { 0 }
				buf.get(ba, 0, nRead)
				if (readData == null) {
					readData = ba
				} else {
					readData += ba
				}
			}
		} while (nRead > 0)
		if (readData == null) {
			return 0
		}
		val old = key.byteArray
		key.byteArray = if (old == null) {
			readData
		} else {
			old + readData
		}
		callFrame(key)
		return readData.size
	}

	private fun callFrame(key: SelectionKey) {
		val newBuf = key.byteArray ?: return
		if (newBuf.isEmpty()) {
			return
		}
		val p = bufferFrame.accept(newBuf)
		val sz = p.first
		when {
			sz > 0 -> {
				if (sz < newBuf.size) {
					key.byteArray = newBuf.sliceArray(sz until newBuf.size)
				} else {
					key.byteArray = null
				}
				if (p.second != null) {
					callback.onReadFrame(key, p.second!!)
				}
				callFrame(key)
			}
			newBuf.size > bufferFrame.maxFrameLength -> {
				key.byteArray = null
			}
			else -> {
				key.byteArray = newBuf
			}
		}
	}
}