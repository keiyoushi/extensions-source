package eu.kanade.tachiyomi.extension.id.aarlas

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Aarlas :
    ZeistManga(
        "Aarlas",
        "https://www.arlas.online",
        "id",
    ) {
    override val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }

    override val preferChapterUpdatedDate = true
}
