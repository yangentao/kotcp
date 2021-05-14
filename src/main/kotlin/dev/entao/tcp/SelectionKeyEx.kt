package dev.entao.tcp

import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.charset.Charset


val ByteArray.strUTF8: String get() = String(this, Charsets.UTF_8)

fun SelectionKey.writeFrame(text: String, charset: Charset = Charsets.UTF_8): Boolean {
	return this.writeFrame(text.toByteArray(charset))
}

fun SelectionKey.writeFrame(data: ByteArray): Boolean {
	val frame = this.framer ?: return false
	return this.write(frame.makeFrame(data))
}

fun SelectionKey.write(data: ByteArray): Boolean {
	if (this.isValid) {
		val ch = this.channel() as SocketChannel
		val b = ByteBuffer.wrap(data)
		while (b.hasRemaining()) {
			val n = ch.write(b)
			if (n < 0) {
				//TODO 待测试
				//导致当前SelectionKey.isValid变成false, 下一个Selector.select时,调用,onKeyInvalid
				this.channel().close()
				this.selector().wakeup()
				return false
			}
			if (n == 0) {//缓冲区满
				Thread.sleep(100)
			}
		}
		return true
	}
	return false
}


@Suppress("UNCHECKED_CAST")
val SelectionKey.attrMap: HashMap<String, Any>
	get() {
		val m = this.attachment()
		if (m is HashMap<*, *>) {
			return m as HashMap<String, Any>
		}
		val map = HashMap<String, Any>()
		this.attach(map)
		return map
	}

fun SelectionKey.attr(key: String, value: Any?) {
	if (value == null) {
		this.attrMap.remove(key)
	} else {
		this.attrMap[key] = value
	}
}

fun SelectionKey.attr(key: String): Any? {
	return this.attrMap[key]
}

var SelectionKey.userId: String?
	get() = this.attr("userId") as? String
	set(value) {
		this.attr("userId", value)
	}


var SelectionKey.byteArray: ByteArray?
	get() = this.attr("byteArray") as? ByteArray
	set(value) {
		this.attr("byteArray", value)
	}

var SelectionKey.readTime: Long
	get() = this.attr("readTime") as? Long ?: 0L
	set(value) {
		this.attr("readTime", value)
	}

var SelectionKey.framer: BufferFrame?
	get() = this.attr("framer") as? BufferFrame
	set(value) {
		this.attr("framer", value)
	}

fun SelectionKey.close() {
	this.cancel()
	this.channel().close()
}