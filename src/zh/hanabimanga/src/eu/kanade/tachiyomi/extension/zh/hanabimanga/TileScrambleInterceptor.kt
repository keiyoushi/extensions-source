package eu.kanade.tachiyomi.extension.zh.hanabimanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import keiyoushi.utils.decodeHex
import keiyoushi.utils.readIntLittleEndian
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TileScrambleInterceptor : Interceptor {

    private val mediaType = "image/png".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment.isNullOrEmpty()) return chain.proceed(request)

        val info = ScrambleInfo.parse(fragment)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val restored = TileRestorer.restore(response.body.bytes(), info)
        return response.newBuilder()
            .body(Buffer().write(restored).asResponseBody(mediaType, restored.size.toLong()))
            .build()
    }
}

private object TicketDecoder {

    private val STATIC_KEY = "bbbd0365d81d8dafda24f7f8d0c16974eaad26faf3610152af92f4571f6dcf45".decodeHex()

    fun decrypt(ticketB64: String, nonceB64: String): String {
        val ticket = Base64.decode(ticketB64, Base64.DEFAULT)
        val nonce = Base64.decode(nonceB64, Base64.DEFAULT)

        require(nonce.size == 12) { "nonce must be 12 bytes, got ${nonce.size}" }
        require(ticket.size >= 17) { "ticket too short: ${ticket.size}" }

        val ciphertext = ticket.copyOf(ticket.size - 16)
        val authTag = ticket.copyOfRange(ticket.size - 16, ticket.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(STATIC_KEY, "AES"), GCMParameterSpec(128, nonce))
        val plain = cipher.doFinal(ciphertext + authTag)
        return plain.toString(Charsets.US_ASCII)
    }
}

private class ChaCha8Rng(key: ByteArray) {

    private val base = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574) +
        IntArray(8) { key.readIntLittleEndian(it * 4) } + intArrayOf(0, 0, 0, 0)

    private var counterLo = 0
    private var counterHi = 0
    private val block = IntArray(16)
    private var blockPos = 16

    fun nextUInt32(): Int {
        if (blockPos == 16) {
            chacha8Block(base, counterLo, counterHi, block)
            counterLo += 1
            if (counterLo == 0) counterHi += 1
            blockPos = 0
        }
        return block[blockPos++]
    }

    private fun chacha8Block(base: IntArray, counterLo: Int, counterHi: Int, out: IntArray) {
        val state = base.copyOf().apply {
            this[12] = counterLo
            this[13] = counterHi
        }
        val x = state.copyOf()
        repeat(4) {
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 1, 5, 9, 13)
            quarterRound(x, 2, 6, 10, 14)
            quarterRound(x, 3, 7, 11, 15)
            quarterRound(x, 0, 5, 10, 15)
            quarterRound(x, 1, 6, 11, 12)
            quarterRound(x, 2, 7, 8, 13)
            quarterRound(x, 3, 4, 9, 14)
        }
        for (i in 0 until 16) out[i] = x[i] + state[i]
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]
        s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] += s[d]
        s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] += s[b]
        s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] += s[d]
        s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }
}

private object ScramblePermutation {

    fun sourceToDest(seedAsciiHex: String, tileCount: Int): IntArray {
        val rng = ChaCha8Rng(MessageDigest.getInstance("SHA-256").digest(seedAsciiHex.encodeToByteArray()))

        val arr = IntArray(tileCount) { it }
        for (len in tileCount downTo 2) {
            val j = ((rng.nextUInt32().toLong() and 0xFFFFFFFFL) % len.toLong()).toInt()
            val i = len - 1
            arr[i] = arr[j].also { arr[j] = arr[i] }
        }
        return arr // arr[source] = dest
    }

    fun destFromSource(seedAsciiHex: String, tileCount: Int): IntArray {
        val d2s = IntArray(tileCount)
        sourceToDest(seedAsciiHex, tileCount).forEachIndexed { source, dest -> d2s[dest] = source }
        return d2s // destFromSource[dest] = source
    }
}

private object TileRestorer {

    fun restore(scrambled: ByteArray, info: ScrambleInfo): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(
            scrambled,
            0,
            scrambled.size,
            BitmapFactory.Options().apply { inMutable = true },
        ) ?: throw IOException("Failed to decode scrambled image bytes")
        val w = bitmap.width
        val h = bitmap.height

        val seed = TicketDecoder.decrypt(info.ticket, info.nonce)
        val perm = ScramblePermutation.destFromSource(seed, info.cols * info.rows)

        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = IntArray(w * h)

        perm.forEachIndexed { dest, source ->
            val st = tileRect(source, w, h, info.cols, info.rows)
            val dt = tileRect(dest, w, h, info.cols, info.rows)
            val copyW = minOf(st.w, dt.w)
            val copyH = minOf(st.h, dt.h)
            for (y in 0 until copyH) {
                val srcOff = (st.y + y) * w + st.x
                val dstOff = (dt.y + y) * w + dt.x
                src.copyInto(dst, dstOff, srcOff, srcOff + copyW)
            }
        }

        val out = ByteArrayOutputStream()
        try {
            bitmap.setPixels(dst, 0, w, 0, 0, w, h)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun tileRect(index: Int, width: Int, height: Int, cols: Int, rows: Int): Tile {
        val col = index % cols
        val row = index / cols
        val x0 = col * width / cols
        val x1 = (col + 1) * width / cols
        val y0 = row * height / rows
        val y1 = (row + 1) * height / rows
        return Tile(x0, y0, x1 - x0, y1 - y0)
    }

    private class Tile(val x: Int, val y: Int, val w: Int, val h: Int)
}
