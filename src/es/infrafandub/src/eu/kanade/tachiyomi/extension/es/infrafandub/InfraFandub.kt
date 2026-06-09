package eu.kanade.tachiyomi.extension.es.infrafandub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class InfraFandub :
    Madara(
        "InfraFandub",
        "https://infrafandub.com",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale("es")),
    ) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override fun popularMangaSelector() = "div.manga-item"
    override val popularMangaUrlSelector = "div.title a"
    override fun searchMangaSelector() = "div.manga-item"
    override val searchMangaUrlSelector = "div.title a"

    override val mangaDetailsSelectorTitle = "h1.series-title"
    override val mangaDetailsSelectorAuthor = "div.series-details div.detail-item:contains(Autor) span.detail-value"
    override val mangaDetailsSelectorArtist = "div.series-details div.detail-item:contains(Artista) span.detail-value"
    override val mangaDetailsSelectorGenre = "div.genres a.genre-tag"
    override val mangaDetailsSelectorDescription = "div.summary-text"
    override val mangaDetailsSelectorThumbnail = "aside.sidebar img.series-cover"
    override val mangaDetailsSelectorStatus = "div.series-details div.detail-item:contains(Estado) span.detail-value"

    override fun chapterListSelector() = "div.chapters-list a.chapter-item"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        with(element) {
            chapter.setUrlWithoutDomain(absUrl("href"))
            chapter.name = selectFirst("span.chapter-number")!!.text()
            chapter.date_upload = parseChapterDate(selectFirst("span.chapter-date")?.text())
        }
        return chapter
    }
}
