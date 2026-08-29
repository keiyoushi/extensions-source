package eu.kanade.tachiyomi.extension.ja.pixivcomic

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun getTimeAndHash(salt: String): Pair<String, String> {
    val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(Date())

    val hash = MessageDigest.getInstance("SHA-256").digest((time + salt).encodeToByteArray()).toHexString()

    return time to hash
}
