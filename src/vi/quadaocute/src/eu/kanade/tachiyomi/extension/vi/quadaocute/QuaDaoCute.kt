package eu.kanade.tachiyomi.extension.vi.quadaocute

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class QuaDaoCute : Madara() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override val mangaSubString = "manga"
    override val genreDirectory = "manga-genre"
    override val filterNonMangaItems = false

    override fun archiveSelector() = ".page-item-detail"
    override val mangaDetailsSelectorTitle = "div.post-title h1, div.post-title h3, #manga-title > h1"
    override val mangaDetailsSelectorAuthor = ".post-content_item:contains(Tác giả) .author-content a"
    override val mangaDetailsSelectorArtist = ".post-content_item:contains(Họa sĩ) .artist-content a"
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(Trạng thái) .summary-content"
    override val mangaDetailsSelectorDescription = ".summary_content .manga-excerpt, .description-summary"
    override val mangaDetailsSelectorThumbnail = ".summary_image img"
    override val mangaDetailsSelectorGenre = ".genres-content a"
    override val mangaDetailsSelectorTag = ".tags-content a"
    override val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"
    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"
    override val filterGenresSelector = ".genres-content"
    override val pageListParseSelector = ".reading-content .text-left:not(:has(.blocks-gallery-item))"
    override val chapterUrlSelector = "> a"
    override val chapterDateSelector = ".chapter-release-date a[title], .chapter-release-date"
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        val statusText = document.selectFirst(mangaDetailsSelectorStatus)?.text()?.lowercase().orEmpty()
        status = when {
            "hoàn thành" in statusText || "hoàn tất" in statusText -> SManga.COMPLETED
            "đang tiến hành" in statusText || "đang cập nhật" in statusText -> SManga.ONGOING
            "tạm ngưng" in statusText || "tạm dừng" in statusText -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    override fun parseChapterDate(date: String?): Long = chapterDateFormat.tryParseDate(date, ZoneId.of("Asia/Ho_Chi_Minh"))
}
