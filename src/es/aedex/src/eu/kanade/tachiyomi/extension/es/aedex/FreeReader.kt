package eu.kanade.tachiyomi.extension.es.aedex

import android.util.Base64
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// The site's free reading mode: a countdown over a WebSocket whose challenges are signed with a per-chapter key
class FreeReader {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    val publicKey: String = keyPair.public.encoded.base64Url()

    suspend fun countdown(client: OkHttpClient, headers: Headers, wsUrl: String, ticket: String): FreeReaderFrameDto {
        // Like the site's client, only ws_url's host is kept
        val url = Request.Builder().url(wsUrl).build().url.newBuilder()
            .encodedPath("/ws/free-reader")
            .query(null)
            .addQueryParameter("ticket", ticket)
            .build()
        val request = Request.Builder().url(url).headers(headers).build()
        return suspendCancellableCoroutine { continuation ->
            val socket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        // Like the site's handler: unparseable and unknown frames are ignored
                        val frame = try {
                            text.parseAs<FreeReaderFrameDto>()
                        } catch (_: SerializationException) {
                            return
                        }
                        when (frame.type) {
                            "countdown" -> frame.challenge?.let {
                                webSocket.send(FreeReaderAckDto("ack", it, sign(it)).toJsonString())
                            }
                            "ready" -> {
                                webSocket.close(1000, null)
                                continuation.resume(frame)
                            }
                            "error", "early_access" -> {
                                webSocket.close(1000, null)
                                continuation.resumeWithException(IOException(frame.code ?: frame.type))
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (continuation.isActive) continuation.resumeWithException(t)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("Free reader closed: $code"))
                    }
                },
            )
            continuation.invokeOnCancellation { socket.cancel() }
        }
    }

    // ECDSA P-256 signature as WebCrypto gives it: raw r||s
    private fun sign(challenge: String): String {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(challenge.toByteArray())
            sign()
        }
        val rLength = der[3].toInt()
        val r = BigInteger(der.copyOfRange(4, 4 + rLength))
        val s = BigInteger(der.copyOfRange(6 + rLength, der.size))
        return (r.toFixed() + s.toFixed()).base64Url()
    }

    private fun BigInteger.toFixed() = toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }

    private fun ByteArray.base64Url() = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
