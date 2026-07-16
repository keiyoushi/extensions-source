package eu.kanade.tachiyomi.extension.ja.pixivcomic

import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal fun getTimeAndHash(salt: String): Pair<String, String> {
    val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
    val offsetMillis = TimeZone.getDefault().getOffset(now.toEpochMilliseconds())
    val time = (now + offsetMillis.milliseconds).toString().removeSuffix("Z") + offsetMillis.toIsoOffset()

    val hash = MessageDigest.getInstance("SHA-256").digest((time + salt).encodeToByteArray()).toHexString()

    return time to hash
}

private fun Int.toIsoOffset(): String {
    val sign = if (this >= 0) "+" else "-"
    val minutes = abs(this) / 60_000
    return "%s%02d:%02d".format(Locale.ROOT, sign, minutes / 60, minutes % 60)
}
