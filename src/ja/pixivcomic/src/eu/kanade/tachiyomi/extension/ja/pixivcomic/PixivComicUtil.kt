package eu.kanade.tachiyomi.extension.ja.pixivcomic

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class NoSuchTagException(message: String) : Exception(message)

internal fun tagInterceptor(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val response = chain.proceed(request)

    if (request.url.pathSegments.contains("tags") && response.code == 404) {
        throw NoSuchTagException("The inputted tag doesn't exist")
    }
    return response
}

internal fun randomString(): String {
    // the average length of key
    val length = (30..40).random()

    return buildString(length) {
        val charPool = ('a'..'z') + ('A'..'Z') + (0..9)

        for (i in 0 until length) {
            append(charPool.random())
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun getTimeAndHash(salt: String): Pair<String, String> {
    val timeFormatted = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH).format(Date())

    val saltedTimeArray = timeFormatted.plus(salt).toByteArray()
    val saltedTimeHash = MessageDigest.getInstance("SHA-256")
        .digest(saltedTimeArray).toUByteArray()
    val hexadecimalTimeHash = saltedTimeHash.joinToString("") {
        var hex = Integer.toHexString(it.toInt())
        if (hex.length < 2) {
            hex = "0$hex"
        }
        return@joinToString hex
    }

    return Pair(timeFormatted, hexadecimalTimeHash)
}
