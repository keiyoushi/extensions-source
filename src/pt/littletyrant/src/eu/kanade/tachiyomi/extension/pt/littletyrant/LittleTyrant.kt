package eu.kanade.tachiyomi.extension.pt.littletyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class LittleTyrant :
    Madara(
        "Little Tyrant",
        "https://tiraninha.world",
        "pt-BR",
        dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // =============================== Popular =================================

    override fun popularMangaSelector() = ".manga-grid .littletyrant-archive-item"

    override val popularMangaUrlSelector = ".card-littletyrant a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst(popularMangaUrlSelector)!!.absUrl("href"))
    }

    // =============================== Details =================================

    override val mangaDetailsSelectorGenre = ".manga-pills-minimal a"
    override val mangaDetailsSelectorDescription = ".manga-summary-premium p"
    override val mangaDetailsSelectorAuthor = ".manga-attributes-grid span:contains(AUTOR) + span"
    override val mangaDetailsSelectorArtist = ".manga-attributes-grid span:contains(ARTISTA) + span"
    override val mangaDetailsSelectorStatus = ".manga-attributes-grid span:contains(STATUS) + span"

    // =============================== Chapters =================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val document = client.newCall(mangaDetailsRequest(manga)).execute().asJsoup()
        val mangaId = document.selectFirst("a.wp-manga-action-button")!!.attr("data-post")
        val chapters = mutableListOf<SChapter>()
        val url = "$baseUrl/wp-admin/admin-ajax.php"
        var offset = 0
        do {
            val form = FormBody.Builder()
                .add("action", "load_more_chapters")
                .add("manga_id", mangaId)
                .add("offset", offset.toString())
                .build()
            offset += 12
            val dto = client.newCall(POST(url, headers, form)).execute().parseAs<ChapterDto>()
            val chapterElements = dto.toJsoup(baseUrl).select(chapterListSelector())
            chapters += chapterElements.map(::chapterFromElement)
        } while (!dto.isEmpty())

        chapters.sortedByDescending(SChapter::chapter_number)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".chapter-name")!!.text()
        date_upload = dateFormat.tryParse(element.selectFirst(".chapter-release-date")?.text())
        // The source chapter list is out of order, so extract the number here for later sorting
        CHAPTER_NUMBER_REGEX.find(name)?.groupValues?.last()?.let {
            chapter_number = it.toFloat()
        }
        setUrlWithoutDomain(element.selectFirst(".chapter-card-link")!!.absUrl("href"))
    }

    // =============================== Pages =================================

    override val pageListParseSelector = ".reading-content img"

    companion object {
        private val CHAPTER_NUMBER_REGEX = """\d+(?:\.\d+)?""".toRegex()
    }
}
