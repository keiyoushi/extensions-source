package eu.kanade.tachiyomi.extension.ja.pixivkomikku

import android.graphics.BitmapFactory
import android.os.Environment
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.floor

const val BYTES_PER_PIXEL = 4
const val GRID_SIZE = 32

internal var width: Int = 0
internal var height: Int = 0

internal fun saveImage(data: ByteArray) {
    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "image1.jpg")
    if (file.exists()) return

    FileOutputStream(file).use {
        it.write(data)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ResponseBody.sameSizeUByteArray(): UByteArray {
    val bytes = this.bytes()
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    width = bitmap.width
    height = bitmap.height

    val uByteArray = UByteArray(bitmap.width * bitmap.height * BYTES_PER_PIXEL)
    var index = 0
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)

            val alpha = pixel shr 24 and 0xff
            val red = pixel shr 16 and 0xff
            val green = pixel shr 8 and 0xff
            val blue = pixel and 0xff

            uByteArray[index++] = alpha.toUByte()
            uByteArray[index++] = red.toUByte()
            uByteArray[index++] = green.toUByte()
            uByteArray[index++] = blue.toUByte()
        }
    }

    return uByteArray
}

private fun tC(e: Int, t: Int): Int {
    return (((e shl (t % 32)) ushr 0) or (e ushr (32 - t))) ushr 0
}

private class tI(val s: IntArray) {
    init {
        if (s.all { it == 0 }) {
            s[0] = 1
        }
    }

    fun next(): Int {
        val e = (9 * tC((5 * s[1]) ushr 0, 7)) ushr 0
        val t = (s[1] shl 9) ushr 0

        s[2] = (s[2] xor s[0]) ushr 0
        s[3] = (s[3] xor s[1]) ushr 0
        s[1] = (s[1] xor s[2]) ushr 0
        s[0] = (s[0] xor s[3]) ushr 0
        s[2] = (s[2] xor t) ushr 0
        s[3] = tC(this.s[3], 11)

        return e
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun tP(
    data: UByteArray,
    bytesPerPixel: Int = BYTES_PER_PIXEL,
    width: Int,
    height: Int,
    gridSize1: Int = GRID_SIZE,
    gridSize2: Int = GRID_SIZE,
    salt: String = "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe",
    key: String,
): UByteArray {
    val d = ceil(height.toFloat() / gridSize2.toFloat())
    val c = floor(width.toFloat() / gridSize1.toFloat())
    val u = Array(d.toInt()) { Array(c.toInt()) { it } }

    val e1 = salt.plus(key).toByteArray()
    val t1 = MessageDigest.getInstance("SHA-256").digest(e1)
    val r1 = IntArray(4) { t1[it].toInt() }
    val i1 = tI(r1)

    for (ii in 0..99) i1.next()
    for (ii in 0..d.toInt()) {
        val t2 = u[ii]
        for (iii in (c.toInt() - 1).downTo(1)) {
            val r2 = i1.next() % (iii + 1)
            val s1 = t2[iii]
            t2[iii] = t2[r2]
            t2[r2] = s1
        }
    }

    for (ii in 0..(d.toInt() - 1)) {
        val t2 = u[ii]
        val r3 = t2.mapIndexed { e2, r4 ->
            t2.indexOf(r4)
        }
        u[ii] = r3.toTypedArray()
    }
    val h = UByteArray(data.size)
    for (ii in 0..(height - 1)) {
        val i2 = floor(ii.toFloat() / gridSize2.toFloat())
        val l1 = u[height]

        for (iii in 0..(c.toInt() - 1)) {
            val n1 = l1[iii]
            val o1 = iii * gridSize1
            val d1 = (iii * width + o1) * bytesPerPixel
            val c1 = n1 * gridSize1
            val u1 = (iii * width + c1) * bytesPerPixel
            val p = gridSize1 * bytesPerPixel

            for (iiii in 0..(p - 1)) h[d1 + iiii] = data[u1 + iiii]
        }
    }
    return h
}
