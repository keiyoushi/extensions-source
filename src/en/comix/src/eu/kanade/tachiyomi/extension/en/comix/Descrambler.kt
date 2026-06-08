package eu.kanade.tachiyomi.extension.en.comix

import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer

object Descrambler {

    private const val ENC_MULTIPLIER = 1000005
    private const val ENC_INCREMENT = 1234567891
    private const val ENC_READ_CHUNK_SIZE = 8192L

    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return@Interceptor response

        val body = response.body
        val bodyMediaType = body.contentType()

        val seed = response.header("x-enc-seed")?.toLongOrNull()?.toInt()
            ?: return@Interceptor response

        if (seed == 0) return@Interceptor response

        val length = response.header("x-enc-len")?.toIntOrNull()
            ?: return@Interceptor response

        response.newBuilder()
            .body(
                body.source()
                    .decodeEncodedPrefix(seed, length)
                    .buffer()
                    .asResponseBody(bodyMediaType, body.contentLength()),
            )
            .build()
    }

    private fun Source.decodeEncodedPrefix(seed: Int, length: Int) = object : ForwardingSource(this) {
        private var state = seed
        private var decoded = 0

        override fun read(sink: Buffer, byteCount: Long): Long {
            val chunk = Buffer()
            val read = super.read(chunk, minOf(byteCount, ENC_READ_CHUNK_SIZE))
            if (read == -1L) return -1L

            val bytes = chunk.readByteArray()
            val limit = minOf(bytes.size, (length - decoded).coerceAtLeast(0))
            for (i in 0 until limit) {
                state = state * ENC_MULTIPLIER + ENC_INCREMENT
                bytes[i] = (bytes[i].toInt() xor (state ushr 24)).toByte()
            }
            decoded += limit

            sink.write(bytes)
            return read
        }
    }
}
