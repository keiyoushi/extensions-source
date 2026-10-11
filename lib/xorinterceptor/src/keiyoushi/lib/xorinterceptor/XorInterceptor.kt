package keiyoushi.lib.xorinterceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * Decrypts or encrypts this [Source] by applying a single-byte XOR [key] mask in-place.
 *
 * @param key the single-byte XOR mask
 * @return a [Source] streaming the XOR-transformed data
 */
fun Source.xor(key: Byte): Source = XorByteSource(this, key)

/**
 * Decrypts or encrypts this [Source] by applying a single-byte XOR [key] mask in-place.
 *
 * @param key the single-byte XOR mask (converted to Byte)
 * @return a [Source] streaming the XOR-transformed data
 */
fun Source.xor(key: Int): Source = xor(key.toByte())

/**
 * Decrypts or encrypts this [Source] by applying a multi-byte XOR [key] cyclically in-place.
 *
 * @param key the non-empty byte array key applied cyclically
 * @return a [Source] streaming the XOR-transformed data
 * @throws IllegalArgumentException if [key] is empty
 */
fun Source.xor(key: ByteArray): Source {
    require(key.isNotEmpty()) { "XOR key must not be empty" }
    return if (key.size == 1) {
        XorByteSource(this, key[0])
    } else {
        XorByteArraySource(this, key)
    }
}

/**
 * Transforms this [ByteArray] in-place by applying a single-byte XOR [key] mask.
 *
 * @param key the single-byte XOR mask
 * @return this [ByteArray] after modification
 */
fun ByteArray.xorInPlace(key: Byte): ByteArray {
    val mask = key.toInt()
    for (i in indices) {
        this[i] = (this[i].toInt() xor mask).toByte()
    }
    return this
}

/**
 * Transforms this [ByteArray] in-place by applying a single-byte XOR [key] mask.
 *
 * @param key the single-byte XOR mask (converted to Byte)
 * @return this [ByteArray] after modification
 */
fun ByteArray.xorInPlace(key: Int): ByteArray = xorInPlace(key.toByte())

/**
 * Transforms this [ByteArray] in-place by applying a multi-byte XOR [key] cyclically.
 *
 * @param key the non-empty byte array key applied cyclically
 * @return this [ByteArray] after modification
 * @throws IllegalArgumentException if [key] is empty
 */
fun ByteArray.xorInPlace(key: ByteArray): ByteArray {
    require(key.isNotEmpty()) { "XOR key must not be empty" }
    val keySize = key.size
    for (i in indices) {
        this[i] = (this[i].toInt() xor key[i % keySize].toInt()).toByte()
    }
    return this
}

/**
 * Transforms a copy of this [ByteArray] by applying a single-byte XOR [key] mask.
 *
 * @param key the single-byte XOR mask
 * @return a new [ByteArray] with the XOR-transformed data
 */
fun ByteArray.xor(key: Byte): ByteArray = copyOf().xorInPlace(key)

/**
 * Transforms a copy of this [ByteArray] by applying a single-byte XOR [key] mask.
 *
 * @param key the single-byte XOR mask (converted to Byte)
 * @return a new [ByteArray] with the XOR-transformed data
 */
fun ByteArray.xor(key: Int): ByteArray = copyOf().xorInPlace(key.toByte())

/**
 * Transforms a copy of this [ByteArray] by applying a multi-byte XOR [key] cyclically.
 *
 * @param key the non-empty byte array key applied cyclically
 * @return a new [ByteArray] with the XOR-transformed data
 * @throws IllegalArgumentException if [key] is empty
 */
fun ByteArray.xor(key: ByteArray): ByteArray = copyOf().xorInPlace(key)

/**
 * Wraps this [ResponseBody] source with a single-byte XOR [key] mask transformation.
 *
 * @param key the single-byte XOR mask
 * @return a new [ResponseBody] with the transformed source
 */
fun ResponseBody.xor(key: Byte): ResponseBody = source().xor(key).buffer().asResponseBody(contentType(), contentLength())

/**
 * Wraps this [ResponseBody] source with a single-byte XOR [key] mask transformation.
 *
 * @param key the single-byte XOR mask (converted to Byte)
 * @return a new [ResponseBody] with the transformed source
 */
fun ResponseBody.xor(key: Int): ResponseBody = xor(key.toByte())

/**
 * Wraps this [ResponseBody] source with a multi-byte cyclic XOR [key] transformation.
 *
 * @param key the non-empty byte array key applied cyclically
 * @return a new [ResponseBody] with the transformed source
 */
fun ResponseBody.xor(key: ByteArray): ResponseBody = source().xor(key).buffer().asResponseBody(contentType(), contentLength())

/**
 * Replaces the [Response.body] with a single-byte XOR [key] mask transformed body.
 *
 * @param key the single-byte XOR mask
 * @return a new [Response] with the transformed body
 */
fun Response.xor(key: Byte): Response = newBuilder().body(body.xor(key)).build()

/**
 * Replaces the [Response.body] with a single-byte XOR [key] mask (converted to Byte) transformed body.
 *
 * @param key the single-byte XOR mask (converted to Byte)
 * @return a new [Response] with the transformed body
 */
fun Response.xor(key: Int): Response = xor(key.toByte())

/**
 * Replaces the [Response.body] with a multi-byte cyclic XOR [key] transformed body.
 *
 * @param key the non-empty byte array key applied cyclically
 * @return a new [Response] with the transformed body
 */
fun Response.xor(key: ByteArray): Response = newBuilder().body(body.xor(key)).build()

/**
 * An [Interceptor] that intercepts successful HTTP responses and applies a XOR transformation
 * using a key derived from the [Request].
 *
 * @param keyProvider a function that extracts the XOR key from the [Request], or null if not applicable
 */
open class XorInterceptor(
    private val keyProvider: (Request) -> ByteArray?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val key = keyProvider(request) ?: return response
        if (key.isEmpty()) return response

        return response.xor(key)
    }
}

/**
 * An [Interceptor] that intercepts successful HTTP responses and applies a XOR transformation
 * using a key extracted from the [Request] URL fragment.
 *
 * @param keyExtractor a function that parses the XOR key from the URL fragment string, or null if not applicable
 */
open class FragmentXorInterceptor(
    keyExtractor: (String) -> ByteArray?,
) : XorInterceptor({ request ->
    request.url.fragment?.takeIf { it.isNotEmpty() }?.let(keyExtractor)
})

private class XorByteSource(
    delegate: Source,
    private val mask: Byte,
) : ForwardingSource(delegate) {
    private val cursor = Buffer.UnsafeCursor()

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read <= 0L) return read

        sink.readAndWriteUnsafe(cursor).use {
            var length = it.seek(sink.size - read)
            val maskInt = mask.toInt()
            while (length != -1) {
                val data = it.data!!
                val start = it.start
                for (i in start until start + length) {
                    data[i] = (data[i].toInt() xor maskInt).toByte()
                }
                length = it.next()
            }
        }
        return read
    }
}

private class XorByteArraySource(
    delegate: Source,
    key: ByteArray,
) : ForwardingSource(delegate) {
    private val cursor = Buffer.UnsafeCursor()
    private val key = key.copyOf()
    private var keyIndex = 0

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read <= 0L) return read

        sink.readAndWriteUnsafe(cursor).use {
            var length = it.seek(sink.size - read)
            val keySize = key.size
            while (length != -1) {
                val data = it.data!!
                val start = it.start
                for (i in start until start + length) {
                    data[i] = (data[i].toInt() xor key[keyIndex].toInt()).toByte()
                    keyIndex = (keyIndex + 1) % keySize
                }
                length = it.next()
            }
        }
        return read
    }
}
