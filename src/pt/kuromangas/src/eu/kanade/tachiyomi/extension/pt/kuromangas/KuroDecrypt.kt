package eu.kanade.tachiyomi.extension.pt.kuromangas

import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class KuroDecrypt : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful) {
            val contentType = response.body?.contentType()
            if (contentType?.subtype != "json") {
                return response
            }

            val body = response.body?.string() ?: return response

            try {
                val json = Json.parseToJsonElement(body).jsonObject

                if (json.containsKey("payload")) {
                    val encryptedPayload = json["payload"]!!.jsonPrimitive.content
                    val decrypted = CryptoAES.decrypt(encryptedPayload, KEY)

                    if (decrypted.isNotEmpty()) {
                        val newBody = decrypted.toResponseBody(JSON_MEDIA_TYPE)
                        return response.newBuilder()
                            .body(newBody)
                            .build()
                    }
                }
            } catch (_: Exception) {
                // Ignore
            }

            val newBody = body.toResponseBody(response.body?.contentType())
            return response.newBuilder()
                .body(newBody)
                .build()
        }

        return response
    }

    companion object {
        private const val KEY = "iFcVEednxSfSGwRTFQUZFgpwapucoEXFmqKbnYrphaMQGEEexM"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
