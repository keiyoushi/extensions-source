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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

                if (json.containsKey("_x_c")) {
                    val encryptedPayload = json["_x_c"]!!.jsonPrimitive.content
                    println("KuroDecrypt: Encrypted payload found")

                    if (secretKey.isEmpty() || salt.isEmpty()) {
                        println("KuroDecrypt: Keys missing, fetching...")
                        fetchSecrets()
                    }

                    var decrypted = decrypt(encryptedPayload)
                    if (decrypted.isEmpty() && (secretKey.isNotEmpty() || salt.isNotEmpty())) {
                        println("KuroDecrypt: Decryption failed, refetching keys...")
                        fetchSecrets()
                        decrypted = decrypt(encryptedPayload)
                    }

                    if (decrypted.isNotEmpty()) {
                        println("KuroDecrypt: Decryption successful")
                        val newBody = decrypted.toResponseBody(JSON_MEDIA_TYPE)
                        return response.newBuilder()
                            .body(newBody)
                            .build()
                    } else {
                        println("KuroDecrypt: Decryption failed after retries")
                    }
                }
            } catch (e: Exception) {
                println("KuroDecrypt: Error during interception: ${e.message}")
            }

            val newBody = body.toResponseBody(response.body?.contentType())
            return response.newBuilder()
                .body(newBody)
                .build()
        }

        return response
    }

    private fun decrypt(encrypted: String): String {
        if (secretKey.isEmpty() || salt.isEmpty()) return ""
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val key = sha256(secretKey + salt + date)
        return CryptoAES.decrypt(encrypted, key)
    }

    private fun fetchSecrets() {
        synchronized(lock) {
            try {
                println("KuroDecrypt: Fetching main page...")
                val request = GET("https://beta.kuromangas.com/")
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return
                response.close()

                val scriptUrl = SCRIPT_REGEX.find(html)?.groupValues?.get(1)
                println("KuroDecrypt: Script URL found: $scriptUrl")
                if (scriptUrl == null) return

                val scriptRequest = GET("https://beta.kuromangas.com$scriptUrl")
                val scriptResponse = client.newCall(scriptRequest).execute()
                val script = scriptResponse.body?.string() ?: return
                scriptResponse.close()

                val secret = SECRET_REGEX.find(script)?.groupValues?.get(1)
                val extractedSalt = SALT_REGEX.find(script)?.groupValues?.get(1)

                println("KuroDecrypt: Secret found: ${secret != null}, Salt found: ${extractedSalt != null}")

                if (secret != null && extractedSalt != null) {
                    secretKey = secret
                    salt = extractedSalt
                }
            } catch (e: IOException) {
                e.printStackTrace()
                println("KuroDecrypt: Error fetching secrets: ${e.message}")
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private var secretKey = ""
        private var salt = ""
        private val lock = Any()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val SCRIPT_REGEX = """src=["'](/assets/index-[^"']+\.js)["']""".toRegex()
        private val SECRET_REGEX = """const \w+\s*=\s*"(\w{50,})"\s*[;,]\s*(?:const\s+)?\w+\s*=\s*\w+\(\w+\)\s*[;,]\s*(?:const\s+)?\w+\s*=\s*[\w.]+\.AES\.decrypt""".toRegex()
        private val SALT_REGEX = """SHA256\(\w+\s*\+\s*"([^"]+)"\s*\+\s*\w+""".toRegex()
    }
}
