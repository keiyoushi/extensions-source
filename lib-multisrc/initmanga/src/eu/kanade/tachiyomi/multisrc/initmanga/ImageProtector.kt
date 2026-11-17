package eu.kanade.tachiyomi.multisrc.initmanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import keiyoushi.utils.parseAs
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

// Port of the site's Init Manga Image Protector reader (imip-reader.js 2.5.0)
internal object ImageProtector {

    fun proof(token: String, chapterId: String): String = hash("$token:$chapterId:imip_v24", chapterId.toInt()).toUInt().toString()

    // Key, then page urls, masked with one stream seeded by the token, the chapter id and the salt
    fun unlock(gate: ImageKeyDto, token: String, chapterId: String): Pair<String, List<String>> {
        val seed = if (gate.s.isNullOrEmpty()) "${token}_$chapterId" else "${token}_${chapterId}_${gate.s}"
        val random = random(hash(seed, 32))
        val maskedKey = Base64.decode(gate.k, Base64.DEFAULT)
        val key = CharArray(32) { i -> ((maskedKey[i].toInt() and 0xFF) xor (random.next() and 0xFF)).toChar() }.concatToString()
        val urls = Base64.decode(gate.u, Base64.DEFAULT).mapIndexed { i, byte ->
            ((byte.toInt() and 0xFF) xor (random.next() and 0xFF) xor key[i % 32].code).toByte()
        }.toByteArray().decodeToString()
        return key to urls.parseAs<List<String>>()
    }

    fun decrypt(data: Buffer, key: String, contentType: MediaType?): ResponseBody? {
        // 32-byte decoy, body, then the masked 32-byte header, a checksum, a 2-byte marker and the mode
        if (data.size < 68 || data.byteAt(data.size - 3) != 0x38 || data.byteAt(data.size - 2) != 0x7E) return null
        val shuffled = when (data.byteAt(data.size - 1)) {
            0x91 -> false
            0x92 -> true
            else -> return null
        }
        val totalLen = data.size - 36
        val scrambleLen = minOf(1024, totalLen - 32).toInt()

        val random = random(hash(key, totalLen.toInt()))
        val bodyMask = IntArray(scrambleLen) { random.next() and 0xFF }
        val headerMask = IntArray(32) { random.next() and 0xFF }

        var checksum = 0x5A
        val header = ByteArray(32) { i ->
            val byte = data.byteAt(totalLen + i) xor key[(i * 13 + 3) % key.length].code xor headerMask[i]
            checksum = checksum xor byte
            byte.toByte()
        }
        if (data.byteAt(data.size - 4) xor 0xA5 != checksum) return null

        data.skip(32)
        val scrambled = data.readByteArray(scrambleLen.toLong())
        for (i in 0 until scrambleLen) {
            val byte = (scrambled[i].toInt() and 0xFF) xor key[(i * 7 + 11) % key.length].code xor bodyMask[i]
            scrambled[i] = ((byte shl 4) or (byte ushr 4)).toByte()
        }
        val image = Buffer().apply {
            write(header)
            write(scrambled)
            write(data, totalLen - 32 - scrambleLen)
        }
        if (!shuffled) return image.asResponseBody(contentType, image.size)

        val bitmap = BitmapFactory.decodeStream(image.peek().inputStream())
        val stripHeight = bitmap.height / STRIPS / 8 * 8
        // The reader draws small images as they are
        if (bitmap.width < 64 || stripHeight < 16) {
            bitmap.recycle()
            return image.asResponseBody(contentType, image.size)
        }
        val result = unshuffle(bitmap, stripHeight, key)
        bitmap.recycle()
        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        result.recycle()
        return output.asResponseBody("image/jpeg".toMediaType(), output.size)
    }

    // The top 16 strips are stored in an order shuffled with the key, the rows under them as they are
    private fun unshuffle(image: Bitmap, stripHeight: Int, key: String): Bitmap {
        val random = random(hash("${key}_imip_s25", STRIPS))
        val strips = IntArray(STRIPS) { it }
        for (i in STRIPS - 1 downTo 1) {
            val j = (random.next() and 0x7FFFFFFF) % (i + 1)
            strips[i] = strips[j].also { strips[j] = strips[i] }
        }
        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(image, 0f, 0f, null)
        strips.forEachIndexed { slot, strip ->
            val src = Rect(0, slot * stripHeight, image.width, (slot + 1) * stripHeight)
            val dst = Rect(0, strip * stripHeight, image.width, (strip + 1) * stripHeight)
            canvas.drawBitmap(image, src, dst, null)
        }
        return result
    }

    private fun hash(text: String, length: Int): Int {
        var hash = 5381
        text.forEach { hash = hash * 33 + it.code }
        return (hash * 33 + (length and 0xFFFF)).takeIf { it != 0 } ?: 1
    }

    private fun random(seed: Int): Iterator<Int> = generateSequence(seed) { x ->
        var next = x xor (x shl 13)
        next = next xor (next ushr 17)
        next xor (next shl 5)
    }.drop(1).iterator()

    private fun Buffer.byteAt(pos: Long): Int = this[pos].toInt() and 0xFF

    private const val STRIPS = 16
}
