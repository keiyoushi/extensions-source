package eu.kanade.tachiyomi.extension.all.mangafire

import android.util.Base64
import okhttp3.Interceptor

class VrfSigner {

    fun interceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url

        if (url.encodedPath.startsWith("/api/")) {
            val params = request.url.queryParameterNames
                .flatMap { key ->
                    request.url.queryParameterValues(key).map { key to it }
                }
                .sortedBy { it.first }

            val sortedQueryUrl = buildString {
                append(request.url.encodedPath.removePrefix("/api"))
                if (params.isNotEmpty()) {
                    append('?')
                    var lastKey = ""
                    var index = 0
                    append(
                        params.joinToString("&") { (key, value) ->
                            // Put index in keys with suffix []
                            val newKey = if (key.endsWith("[]")) {
                                if (lastKey != key) index = 0
                                lastKey = key
                                key.replace("[]", "[${index++}]")
                            } else {
                                key
                            }
                            "$newKey=$value"
                        },
                    )
                }
            }

            val builder = request.url.newBuilder().query(null)
            params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            builder.addQueryParameter("vrf", sign(sortedQueryUrl))

            val newUrl = builder.build()
            val newRequest = request.newBuilder().url(newUrl).build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }

    fun sign(path: String): String {
        var data = path.toByteArray(Charsets.UTF_8)

        for ((table, key, iv) in STAGES) {
            data = encryptStage(data, table, key, iv)
        }

        return Base64.encodeToString(
            data,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    private fun encryptStage(
        data: ByteArray,
        table: ByteArray,
        key: ByteArray,
        iv: Int,
    ): ByteArray {
        val out = ByteArray(data.size)
        var prev = iv
        val keySize = key.size

        for (i in data.indices) {
            prev = table[
                (data[i].toInt() xor key[i % keySize].toInt() xor prev) and 0xFF,
            ].toInt() and 0xFF
            out[i] = prev.toByte()
        }

        return out
    }

    companion object {
        private const val TABLE_1 =
            "yINlmUNho8VYJT+ibTIP+9ESiULpVEtMOoD6U6lRE0R/xwXo/Xp9NrUgC4cw/Lmo33vUyjUE40kUoEWIr/fxfNNcq2s79ShQ5NhNrFnJ4hXPwOu/SuXzIbuTQKGFvfm08E9jvCfqAtoDqvQq3dVWPQFmJjgvkISBeXY3BgANR+yVnjGbcxZ47d6kLNfZPIayTq3/YGySb1KuVZodWp/WGNAO5pfMcpaK53Hhs0allBszaMaxuouOwdxbwgxIw6YunSsXjI05Yi0j9j4eHKfSXR8Ifo/Od+8iamRfCXTyvm7NGRGYdcQ0ywcK/u6RXhrbcCm4t2eCtrDgQVecJGkQ+A=="

        private const val KEY_1 = "0Ec58JOY3uBzJK9m3zqIOpdlF7UFiax9DmA="

        private const val TABLE_2 =
            "IUFltCxD3Oc2cwCgkJffthaOg9cgPUb0LgW6H/VtfcF0kc5F25t+aWj6JH9VOhOaY0rAFdUxlDnl5BLNvwEJvQtP5qcw7vdb/K+chnbwnspSHT8mz5lqwz41TezG0hkO06FTjJZhsyNuFLDpD2ZZxQj/QIRcF90zpmQ7Byu483WsQqUE0C342HL+JXngRB6fRzxRyVTaKu83h7UYTJ0QMt6ixFh6S3F8gqkKwrGTL3jHNBsD45UnifK8+RGtishQV2K3rujLKEkiZxpr2dYcudFW4oFsDKhad3CLBvuyTqsCo4B7mL5IKQ1vXo/MOOvq1I1d8ar9X6Ttu5KF4fZgiA=="

        private const val KEY_2 = "AAdjb1iPY8CiDmq9H34tKTBF8a3oDQ=="

        private const val TABLE_3 =
            "NQHlu1/wVO5EmkwQymF810qqY2xG1k2obcas4Z9mCsPEIFl9pRIjFxbJ7ybMHbBckT5Ton85E0FOeHezbh/mjlEYpmpnlXOS8dgrqeq2KfxImTh1YK9y0PeMNhzA1OQzSY9brYOJq/l2QnE/hwOeZIhPixVSKIUlDb5vLcH6RWKxkIEMuP0bDwIqQ71AJJaEaMJL7A6YtyIwoRT+L5v4aZzodN/0+3nOGsfblFjgxSfPzVDjNFeNl5P26+kEC/8AHgdrpAbt3hHz3HrRN1Y6e+JHgF7ncFWnoF0y3THL1S71WgWGCa6KtSzTCCG58n68nTyj2T3Sshk7utqCtMi/ZQ=="

        private const val KEY_3 = "DELOJgPsVaCcblDtTGMdHzM="

        private val STAGES: List<Triple<ByteArray, ByteArray, Int>> = listOf(
            Triple(
                Base64.decode(TABLE_1, Base64.DEFAULT),
                Base64.decode(KEY_1, Base64.DEFAULT),
                0x5A,
            ),
            Triple(
                Base64.decode(TABLE_2, Base64.DEFAULT),
                Base64.decode(KEY_2, Base64.DEFAULT),
                0x35,
            ),
            Triple(
                Base64.decode(TABLE_3, Base64.DEFAULT),
                Base64.decode(KEY_3, Base64.DEFAULT),
                0xBA,
            ),
        )
    }
}
