package eu.kanade.tachiyomi.extension.id.aarlas

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class Aarlas : ZeistManga() {
    override val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }

    override val preferChapterUpdatedDate = true
}
