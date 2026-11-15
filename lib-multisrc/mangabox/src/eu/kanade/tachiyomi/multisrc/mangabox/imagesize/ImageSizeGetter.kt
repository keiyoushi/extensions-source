package eu.kanade.tachiyomi.multisrc.mangabox.imagesize

import java.io.InputStream

abstract class ImageSizeGetter(
    val stream: InputStream,
) {
    fun get(): ImageSize? = try {
        if (validate()) {
            calculate()
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        stream.close()
    }

    protected abstract fun validate(): Boolean

    protected abstract fun calculate(): ImageSize

    protected var offset = 0

    protected fun read(b: ByteArray, off: Int) {
        if (offset > off) {
            throw IndexOutOfBoundsException()
        }

        offset += stream.skip((off - offset).toLong()).toInt()

        if (offset != off) {
            throw IndexOutOfBoundsException()
        }

        offset += stream.read(b)

        if (offset != off + b.size) {
            throw IndexOutOfBoundsException()
        }
    }

    protected fun read(off: Int, len: Int): ByteArray = ByteArray(len).also {
        read(it, off)
    }

    protected fun compare(cmp: ByteArray, off: Int): Boolean {
        val b = read(off, cmp.size)
        for (i in 0..<b.size) {
            if (b[i] != cmp[i]) {
                return false
            }
        }
        return true
    }

    protected fun readUint8(off: Int): UByte = read(off, 1)[0].toUByte()

    protected fun readInt8(off: Int): Byte = read(off, 1)[0]

    protected fun readUint16LE(off: Int): UInt = read(off, 2).let {
        it[0].toUByte().toUInt() or (it[1].toUByte().toUInt() shl 8)
    }

    protected fun readUint16BE(off: Int): UInt = read(off, 2).let {
        it[1].toUByte().toUInt() or (it[0].toUByte().toUInt() shl 8)
    }

    protected fun readInt16LE(off: Int): Int = read(off, 2).let {
        it[0].toUByte().toInt() or (it[1].toInt() shl 8)
    }

    protected fun readInt16BE(off: Int): Int = read(off, 2).let {
        it[1].toUByte().toInt() or (it[0].toInt() shl 8)
    }

    protected fun readUint24LE(off: Int): UInt = read(off, 3).let {
        it[0].toUByte().toUInt() or (it[1].toUByte().toUInt() shl 8) or (it[2].toUByte().toUInt() shl 16)
    }

    protected fun readUint24BE(off: Int): UInt = read(off, 3).let {
        it[2].toUByte().toUInt() or (it[1].toUByte().toUInt() shl 8) or (it[0].toUByte().toUInt() shl 16)
    }

    protected fun readInt24LE(off: Int): Int = read(off, 3).let {
        it[0].toUByte().toInt() or (it[1].toUByte().toInt() shl 8) or (it[2].toInt() shl 16)
    }

    protected fun readInt24BE(off: Int): Int = read(off, 3).let {
        it[2].toUByte().toInt() or (it[1].toUByte().toInt() shl 8) or (it[0].toInt() shl 16)
    }

    protected fun readUint32LE(off: Int): UInt = read(off, 4).let {
        it[0].toUByte().toUInt() or (it[1].toUByte().toUInt() shl 8) or (it[2].toUByte().toUInt() shl 16) or (it[3].toUByte().toUInt() shl 24)
    }

    protected fun readUint32BE(off: Int): UInt = read(off, 4).let {
        it[3].toUByte().toUInt() or (it[2].toUByte().toUInt() shl 8) or (it[1].toUByte().toUInt() shl 16) or (it[0].toUByte().toUInt() shl 24)
    }

    protected fun readInt32LE(off: Int): Int = read(off, 4).let {
        it[0].toUByte().toInt() or (it[1].toUByte().toInt() shl 8) or (it[2].toUByte().toInt() shl 16) or (it[3].toInt() shl 24)
    }

    protected fun readInt32BE(off: Int): Int = read(off, 4).let {
        it[3].toUByte().toInt() or (it[2].toUByte().toInt() shl 8) or (it[1].toUByte().toInt() shl 16) or (it[0].toInt() shl 24)
    }
}
