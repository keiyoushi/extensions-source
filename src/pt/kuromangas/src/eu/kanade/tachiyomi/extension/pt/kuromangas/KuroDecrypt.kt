package eu.kanade.tachiyomi.extension.pt.kuromangas

import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class KuroDecrypt : Interceptor {

    private val client = OkHttpClient()

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

                    if (currentKey.isEmpty()) {
                        fetchKey()
                    }

                    var decrypted = decrypt(encryptedPayload)
                    if (decrypted.isEmpty() && currentKey.isNotEmpty()) {
                        fetchKey()
                        decrypted = decrypt(encryptedPayload)
                    }

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

    private fun decrypt(encrypted: String): String {
        if (currentKey.isEmpty()) return ""
        return CryptoAES.decrypt(encrypted, currentKey)
    }

    private fun fetchKey() {
        synchronized(lock) {
            try {
                val request = GET("https://beta.kuromangas.com/")
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return
                response.close()

                val scriptUrl = SCRIPT_REGEX.find(html)?.groupValues?.get(1) ?: return

                val scriptRequest = GET("https://beta.kuromangas.com$scriptUrl")
                val scriptResponse = client.newCall(scriptRequest).execute()
                val script = scriptResponse.body?.string() ?: return
                scriptResponse.close()

                val key = KEY_REGEX.find(script)?.groupValues?.get(1)
                if (key != null) {
                    currentKey = key
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private var currentKey = ""
        private val lock = Any()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val SCRIPT_REGEX = """src="(/assets/index-[^"]+\.js)"""".toRegex()
        private val KEY_REGEX = """const \w+="(\w{50,})",\w+=[\w.]+\.AES\.decrypt""".toRegex()
    }
}
