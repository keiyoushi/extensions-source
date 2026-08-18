package eu.kanade.tachiyomi.extension.pt.remangas

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest

class SignatureInterceptor(private val baseUrl: () -> String) : Interceptor {

    @Volatile
    private var signer: Signer? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith(API_PATH)) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request.signedWith(signer ?: chain.fetchSigner(request)))
        if (response.code != 401) {
            return response
        }

        response.close()
        return chain.proceed(request.signedWith(chain.fetchSigner(request)))
    }

    private fun Interceptor.Chain.fetchSigner(request: Request): Signer {
        val signerRequest = Request.Builder()
            .url("${baseUrl()}$SIGNER_PATH")
            .headers(request.headers)
            .build()

        val script = proceed(signerRequest).use { response ->
            if (!response.isSuccessful) {
                throw IOException("Não foi possível assinar a requisição (HTTP ${response.code})")
            }
            response.body.string()
        }

        return script.toSigner().also { signer = it }
    }

    private fun Request.signedWith(signer: Signer): Request = newBuilder()
        .header("X-Site-ID", SITE_ID)
        .header("X-Web-Slot", signer.slot)
        .header("X-Web-Token", signer.token)
        .header("X-Web-Signature", signer.sign(method, url.encodedPath))
        .build()

    private fun String.toSigner(): Signer {
        val parts = ARRAY_REGEX.find(this)
            ?.groupValues?.get(1)
            ?.let { array -> STRING_REGEX.findAll(array).map { it.groupValues[1].reversed() }.toList() }
        val slotIndex = SLOT_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull()
        val keyBounds = KEY_REGEX.find(this)?.destructured?.let { (start, end) -> start.toInt() to end.toInt() }
        val tokenStart = TOKEN_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull()

        if (parts == null || slotIndex == null || keyBounds == null || tokenStart == null) {
            throw IOException("Falha ao interpretar o assinador do site")
        }

        return Signer(
            slot = parts[slotIndex],
            key = parts.subList(keyBounds.first, keyBounds.second).joinToString(""),
            token = parts.subList(tokenStart, parts.size).joinToString(""),
        )
    }

    private class Signer(val slot: String, val key: String, val token: String) {
        fun sign(method: String, path: String): String {
            val payload = listOf(method.uppercase(), path, SITE_ID, slot, token, key).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }

    companion object {
        private const val API_PATH = "/api/v1/"
        private const val SIGNER_PATH = "/_nix/signer.js"
        private const val SITE_ID = "00000000-0000-0000-0000-000000000003"

        private val ARRAY_REGEX = """z\s*=\s*\[(.*?)]""".toRegex()
        private val STRING_REGEX = """"([^"]*)"""".toRegex()
        private val SLOT_REGEX = """=\s*r\(z\[(\d+)]\)""".toRegex()
        private val KEY_REGEX = """k\s*=\s*j\(z\.slice\((\d+)\s*,\s*(\d+)\)\)""".toRegex()
        private val TOKEN_REGEX = """t\s*=\s*j\(z\.slice\((\d+)\)\)""".toRegex()
    }
}
