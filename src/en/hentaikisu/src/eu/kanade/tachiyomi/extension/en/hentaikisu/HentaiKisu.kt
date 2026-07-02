package eu.kanade.tachiyomi.extension.en.hentaikisu

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class HentaiKisu : HttpSource() {

    override val supportsLatest = false

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/backend/infinite.index.php?p=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<Dto>>().map { it.toSManga() }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select("div.book-list a")

        val mangas = elements.mapNotNull { element ->
            runCatching {
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("div.book-description p")!!.text()
                    thumbnail_url = element.selectFirst("img.lozad")?.attr("abs:data-src")
                }
            }.getOrNull()
        }

        return MangasPage(mangas, false)
    }

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div#info h1")!!.text()
            thumbnail_url = document.selectFirst("div#cover img")?.attr("abs:src")
            artist = document.selectFirst("div.tag-container:contains(Artist:) span.tags")?.text()
            genre = document.select("div.tag-container:contains(Categories:) span.tags a.tag")
                .joinToString { it.ownText() }
            author = document.selectFirst("div.tag-container:contains(Group:) span.tags")?.text()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            url = response.request.url.encodedPath
            name = "Chapter"
            date_upload = 0L
        },
    )

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val readUrl = chapter.url.replace("/g/", "/read/")
        return GET(baseUrl + readUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(la =)")?.data()
            ?: throw Exception("Could not find page data")

        val base64Data = LA_REGEX.find(scriptContent)?.groupValues?.get(1)
            ?: throw Exception("Could not extract base64 data")

        val decodedString = String(Base64.decode(base64Data, Base64.DEFAULT))

        return decodedString.split(",").mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val LA_REGEX = Regex("""la\s*=\s*'([A-Za-z0-9+/=]+)'""")
    }
}
