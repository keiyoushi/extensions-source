package eu.kanade.tachiyomi.extension.vi.truyentini

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TruyenTini : Madara() {
    override val mangaSubString = "truyen"

    override val genreDirectory = "the-loai"

    override val altNameSelector = ".post-content_item:contains(Tên Khác) .summary-content"

    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi"))

    override val chapterMode = ChapterMode.MangaAjax

    override val ongoingStatus = super.ongoingStatus + "đang dịch"

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? {
        val processed = super.processThumbnail(url, fromSearch)
        return processed?.replace(thumbnailOriginalUrlRegex, "$1")
    }
}
