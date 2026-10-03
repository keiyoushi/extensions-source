package eu.kanade.tachiyomi.extension.ja.pixivcomic

import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

internal fun getTimeAndHash(salt: String): Pair<String, String> {
    val time = ZonedDateTime.now().format(timeFormat)
    val hash = MessageDigest.getInstance("SHA-256").digest((time + salt).encodeToByteArray()).toHexString()

    return time to hash
}
