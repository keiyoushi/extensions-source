package eu.kanade.tachiyomi.extension.ja.pixivcomic

import android.annotation.SuppressLint
import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

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
    val timeFormatted = if (Build.VERSION.SDK_INT < 24) {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).format(Date())
            .plus(getCurrentTimeZoneOffsetString())
    } else {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH).format(Date())
    }

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

/**
 * workaround to retrieve time zone offset for android with version lower than 24
 */
@SuppressLint("DefaultLocale")
private fun getCurrentTimeZoneOffsetString(): String {
    val timeZone = TimeZone.getDefault()
    val offsetInMillis = timeZone.rawOffset

    val hours = offsetInMillis / (1000 * 60 * 60)
    val minutes = (offsetInMillis % (1000 * 60 * 60)) / (1000 * 60)

    val sign = if (hours >= 0) "+" else "-"
    val formattedHours = String.format("%02d", abs(hours))
    val formattedMinutes = String.format("%02d", abs(minutes))

    return "$sign$formattedHours:$formattedMinutes"
}
