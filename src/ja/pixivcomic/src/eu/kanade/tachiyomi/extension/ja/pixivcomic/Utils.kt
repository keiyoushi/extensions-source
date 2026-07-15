package eu.kanade.tachiyomi.extension.ja.pixivcomic

import android.os.Build
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

internal fun getTimeAndHash(salt: String): Pair<String, String> {
    val now = Date()
    val time = if (Build.VERSION.SDK_INT >= 24) {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(now)
    } else {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(now) + now.zoneOffset()
    }

    val hash = MessageDigest.getInstance("SHA-256").digest((time + salt).encodeToByteArray()).toHexString()

    return time to hash
}

private fun Date.zoneOffset(): String {
    val offset = TimeZone.getDefault().getOffset(time)
    val sign = if (offset >= 0) "+" else "-"
    val minutes = abs(offset) / 60_000
    return "%s%02d:%02d".format(Locale.ROOT, sign, minutes / 60, minutes % 60)
}
