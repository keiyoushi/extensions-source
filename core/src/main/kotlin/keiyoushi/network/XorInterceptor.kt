package keiyoushi.network

import keiyoushi.utils.xor
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response

/**
 * An [Interceptor] that intercepts successful HTTP responses and applies a XOR transformation
 * using a key derived from the [Request].
 *
 * @param contentType optional explicit [MediaType] override for the decrypted response body
 * @param keyProvider a function that extracts the XOR key from the [Request], or null if not applicable
 */
open class XorInterceptor(
    private val contentType: MediaType? = null,
    private val keyProvider: (Request) -> ByteArray?,
) : Interceptor {
    constructor(keyProvider: (Request) -> ByteArray?) : this(null, keyProvider)

    constructor(
        key: ByteArray,
        contentType: MediaType? = null,
        predicate: (Request) -> Boolean = { true },
    ) : this(contentType, { if (predicate(it)) key else null })

    constructor(
        key: Byte,
        contentType: MediaType? = null,
        predicate: (Request) -> Boolean = { true },
    ) : this(byteArrayOf(key), contentType, predicate)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val key = keyProvider(request)?.takeIf { it.isNotEmpty() } ?: return response

        return response.xor(key, contentType ?: response.body.contentType())
    }
}
