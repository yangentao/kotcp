@file:Suppress("unused")

package dev.entao.tcp

import java.nio.charset.Charset


interface PacketCoder<T> {
	fun decode(data: ByteArray): T
	fun encode(value: T): ByteArray
}


class StringCoder(private val charset: Charset = Charsets.UTF_8) : PacketCoder<String> {
	override fun decode(data: ByteArray): String {
		return data.toString(charset)
	}

	override fun encode(value: String): ByteArray {
		return value.toByteArray(charset)
	}

}

