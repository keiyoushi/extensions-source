package eu.kanade.tachiyomi.extension.vi.hongtrateam

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HongTraTeam : Madara() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override val chapterMode = ChapterMode.MangaAjax
    override val mangaSubString = "truyen"
    override val genreDirectory = "the-loai"
    override val filterNonMangaItems = false

    override fun archiveSelector() = ".page-item-detail, .c-tabs-item__content"
    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorAuthor = ".post-content_item:contains(Tác giả) .summary-content"
    override val mangaDetailsSelectorArtist = ".post-content_item:contains(Họa sĩ) .summary-content"
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(Tình trạng) .summary-content"
    override val mangaDetailsSelectorDescription = ".description-summary"
    override val mangaDetailsSelectorThumbnail = ".summary_image img"
    override val mangaDetailsSelectorGenre = ".genres-content a"
    override val filterGenresSelector = ".genres-content"
    override val pageListParseSelector = ".reading-content img.wp-manga-chapter-img"
    override val chapterDateSelector = ".chapter-release-date .timediff [title], .chapter-release-date .timediff"
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        val statusText = document.selectFirst(mangaDetailsSelectorStatus)?.text()?.lowercase().orEmpty()
        status = when {
            "hoàn thành" in statusText || "hoàn tất" in statusText -> SManga.COMPLETED
            "đang tiến hành" in statusText || "đang cập nhật" in statusText -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override fun parseChapterDate(date: String?): Long = chapterDateFormat.tryParseDate(date, ZoneId.of("Asia/Ho_Chi_Minh"))
}
