@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.entao.tcp

import java.nio.ByteOrder


interface BufferFrame {
    val maxFrameLength: Int
    fun accept(buf: ByteArray): Pair<Int, ByteArray?>
    fun makeFrame(data: ByteArray): ByteArray {
        return data
    }
}

private const val NL: Byte = 0
private const val LK: Byte = 123 // {
private const val RK: Byte = 125 // }
private const val QT: Byte = 34  // "
private const val ES: Byte = 92  // \
private const val SP: Byte = 32  // 空格
private const val CR: Byte = 13  // CR
private const val LF: Byte = 10  // LF
private const val TB: Byte = 9   // TAB
private val witeSpaces: Set<Byte> = setOf(SP, CR, LF, TB)

//只支持utf8 或 ascii
class JsonObjectFrame(val trim: Boolean = true, override val maxFrameLength: Int = 2048) : BufferFrame {

    override fun makeFrame(data: ByteArray): ByteArray {
        return data
    }

    override fun accept(buf: ByteArray): Pair<Int, ByteArray?> {
        if (buf.isEmpty()) {
            return 0 to null
        }
        var fromIndex = 0
        if (trim) {
            while (buf[fromIndex] != NL && (buf[fromIndex] in witeSpaces)) {
                fromIndex += 1
            }
        }
        if (fromIndex >= buf.size) {
            return 0 to null
        }
        if (buf[fromIndex] != LK) {
            return 0 to null
        }
        var lkCount = 1
        var escaping = false
        var inString = false
        for (i in fromIndex + 1 until buf.size) {
            if (inString) {
                if (escaping) {
                    escaping = false
                } else if (buf[i] == QT) {
                    inString = false
                } else if (buf[i] == ES) {
                    escaping = true
                }
                continue
            }
            when (buf[i]) {
                QT -> inString = true
                LK -> lkCount += 1
                RK -> lkCount -= 1
            }
            if (lkCount == 0) {
                return (i + 1) to buf.sliceArray(fromIndex..i)
            }
        }
        return 0 to null
    }

}


class FixLengthFrame(val length: Int, override val maxFrameLength: Int = 2048) : BufferFrame {

    override fun makeFrame(data: ByteArray): ByteArray {
        return ByteArray(length) {
            if (it < data.size) data[it] else 0
        }
    }

    override fun accept(buf: ByteArray): Pair<Int, ByteArray?> {
        if (buf.size >= length) {
            return length to buf.sliceArray(0 until length)
        }
        return 0 to null
    }
}

class EndEdgeFrame(private val end: ByteArray, override val maxFrameLength: Int = 2048) : BufferFrame {

    init {
        assert(end.isNotEmpty())
    }

    override fun makeFrame(data: ByteArray): ByteArray {
        return data + end
    }

    override fun accept(buf: ByteArray): Pair<Int, ByteArray?> {
        if (buf.size < end.size) {
            return 0 to null
        }

        for (i in buf.indices) {
            if (i + end.size <= buf.size) {
                var acceptEnd = true
                for (k in end.indices) {
                    if (end[k] != buf[i + k]) {
                        acceptEnd = false
                        break
                    }
                }
                if (acceptEnd) {
                    return (i + end.size) to buf.sliceArray(0 until i)
                }
            }
        }
        return 0 to null
    }
}

class EdgeFrame(private val start: ByteArray, private val end: ByteArray, override val maxFrameLength: Int = 2048) :
    BufferFrame {

    init {
        assert(start.isNotEmpty() && end.isNotEmpty())
    }

    override fun makeFrame(data: ByteArray): ByteArray {
        return start + data + end
    }

    override fun accept(buf: ByteArray): Pair<Int, ByteArray?> {
        if (buf.size < start.size + end.size) {
            return 0 to null
        }
        for (i in start.indices) {
            if (buf[i] != start[i]) {
                return 0 to null
            }
        }
        for (i in start.size until buf.size) {
            if (i + end.size <= buf.size) {
                var acceptEnd = true
                for (k in end.indices) {
                    if (end[k] != buf[i + k]) {
                        acceptEnd = false
                        break
                    }
                }
                if (acceptEnd) {
                    return (i + end.size) to buf.sliceArray(start.size until i)
                }
            }
        }
        return 0 to null
    }
}

@Suppress("PrivatePropertyName")
class LineFrame(override val maxFrameLength: Int = 2048) : BufferFrame {
    private val CR: Byte = 13
    private val LF: Byte = 10

    override fun makeFrame(data: ByteArray): ByteArray {
        return data + byteArrayOf(10)
    }

    override fun accept(buf: ByteArray): Pair<Int, ByteArray?> {
        for (i in buf.indices) {
            if (buf[i] == CR || buf[i] == LF) {
                val data = buf.sliceArray(0 until i)
                var k = i + 1
                while (k < buf.size) {
                    if (buf[k] == CR || buf[k] == LF) {
                        ++k
                    } else {
                        break
                    }
                }
                return k to data
            }
        }
        return 0 to null
    }

}

class SizeFrame(
    private val byteSize: Int = 4,
    private val order: ByteOrder = ByteOrder.BIG_ENDIAN,
    override val maxFrameLength: Int = 2048
) : BufferFrame {

    init {
        assert(byteSize in 1..4)
    }

    override fun makeFrame(data: ByteArray): ByteArray {
        return data.size.toByteArray(order) + data
    }

    private fun parseSize(buf: ByteArray): Int {
        var n = 0
        if (order == ByteOrder.BIG_ENDIAN) {
            for (i in 0 until byteSize) {
                val v = buf[i].toInt() and 0x00ff
                n = n shl 8
                n = n or v
            }
        } else {
            for (i in 0 until byteSize) {
                val v = (buf[i].toInt() and 0x00ff)
                n = n or (v shl (i * 8))
            }
        }
        return n
    }

    override fun accept(buf: ByteArray): Pair<Int, ByteArray?> {
        if (buf.size < byteSize) {
            return 0 to null
        }
        val sz = parseSize(buf)
        if (sz == 0) {
            return byteSize to null
        }

        val n = byteSize + sz
        if (buf.size >= n) {
            return n to buf.sliceArray(byteSize until n)
        }
        return 0 to null
    }
}

fun Int.toByteArray(order: ByteOrder = ByteOrder.BIG_ENDIAN): ByteArray {
    val b0: Byte = ((this shr 24) and 0x00ff).toByte()
    val b1: Byte = ((this shr 16) and 0x00ff).toByte()
    val b2: Byte = ((this shr 8) and 0x00ff).toByte()
    val b3: Byte = (this and 0x00ff).toByte()
    return if (order == ByteOrder.BIG_ENDIAN) {
        byteArrayOf(b0, b1, b2, b3)
    } else {
        byteArrayOf(b3, b2, b1, b0)
    }
}
//
//fun testFixLength() {
//	val buf = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
//	val f = FixLengthFrame(3)
//	val p = f.accept(buf)
//	logd(p.first, p.second?.joinToString(",") { it.toString() })
//
//
//}
//
//fun testFrame(buf: ByteArray, f: BufferFrame) {
//	val p = f.accept(buf)
//	logd(p.first, p.second?.joinToString(",") { it.toString() })
//}
//
//fun testFrames() {
//	val buf = byteArrayOf(0, 6, 3, 4, 5, 6, 7, 8, 9, 10, 13, 10, 13, 14)
//	testFrame(buf, FixLengthFrame(3))
//	testFrame(buf, EndEdgeFrame(byteArrayOf(4, 5)))
//	testFrame(buf, EdgeFrame(byteArrayOf(0, 6), byteArrayOf(8, 9)))
//	testFrame(buf, LineFrame())
////	testFrame(buf, SizeFrame(1))
//	testFrame(buf, SizeFrame(2))
//}
//
//fun testJsonFrame() {
//	val s = """
//		{"name":"yang","addr":"{,hello"} {"age":22}
//	""".trimIndent()
//	val buf = s.toByteArray()
//	val f = JsonObjectFrame()
//	val n = f.accept(buf)
//	logd(buf.size, n.first, n.second)
//}
//
//
//fun main() {
//	testJsonFrame()
//	testFrames()
//}