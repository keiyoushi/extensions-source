package eu.kanade.tachiyomi.extension.ja.mangamate

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class MangaMate : MangaThemesia(
    "漫画メイト",
    "https://manga-mate.org",
    "ja",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ja")),
) {
    override val seriesAuthorSelector = ".fmed b:contains(作者) + span"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(連載状況) i"

    override fun String?.parseStatus(): Int = when (this) {
        "連載中" -> SManga.ONGOING
        "完結" -> SManga.COMPLETED
        "人気" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
