package eu.kanade.tachiyomi.extension.pt.inkapk

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Inkapk : Madara() {
    override val dateFormat = SimpleDateFormat("MM dd, yyyy", Locale("pt", "BR"))
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString = "obras"

    override val useNewChapterEndpoint = true

    // ===================================== Popular ==========================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?sort=views", headers)

    override fun popularMangaSelector() = ".ink-arc-body a.ink-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".ink-card-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector(): String = ".next.page-numbers"

    // ===================================== Latest ===========================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?sort=date", headers)

    // ===================================== Details ==========================================

    override val mangaDetailsSelectorTitle = ".ink-det-title"
    override val mangaDetailsSelectorThumbnail = ".ink-det-cover img"
    override val mangaDetailsSelectorDescription = ".ink-det-desc"
    override val mangaDetailsSelectorGenre = ".ink-det-genres .ink-genre-pill"
    override val mangaDetailsSelectorStatus = ".lbl:contains(Status) + span"
    override val mangaDetailsSelectorAuthor = ".lbl:contains(Autor) + span"
    override val mangaDetailsSelectorArtist = ".lbl:contains(Arte) + span"

    // ===================================== Chapters =========================================

    override fun chapterListSelector() = ".ink-ch-list .ink-ch-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".ink-ch-item-name")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".ink-ch-item-date")?.text())
        setUrlWithoutDomain(element.absUrl("href"))
    }
}
