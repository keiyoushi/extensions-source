package eu.kanade.tachiyomi.extension.ja.pixivcomic

import keiyoushi.utils.sha256
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

internal fun getTimeAndHash(salt: String): Pair<String, String> {
    val time = ZonedDateTime.now().format(timeFormat)
    val hash = (time + salt).sha256().toHexString()

    return time to hash
}
