package eu.kanade.tachiyomi.extension.en.hentaikisu

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class HentaiKisu : KeiSource() {

    override val supportsLatest = false

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/backend/infinite.index.php?p=$page")
            .parseAs<List<Dto>>()
            .map { it.toSManga() }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        val document = client.get(url).asJsoup()
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
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val chapterUrl = response.request.url.encodedPath
        val document = response.asJsoup()

        val details = SManga.create().apply {
            title = document.selectFirst("div#info h1")!!.text()
            thumbnail_url = document.selectFirst("div#cover img")?.attr("abs:src")
            artist = document.selectFirst("div.tag-container:contains(Artist:) span.tags")?.text()
            genre = document.select("div.tag-container:contains(Categories:) span.tags a.tag")
                .joinToString { it.ownText() }
            author = document.selectFirst("div.tag-container:contains(Group:) span.tags")?.text()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapterList = listOf(
            SChapter.create().apply {
                url = chapterUrl
                name = "Chapter"
                date_upload = 0L
            },
        )

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val readUrl = chapter.url.replace("/g/", "/read/")
        val document = client.get(baseUrl + readUrl).asJsoup()
        val scriptContent = document.selectFirst("script:containsData(la =)")?.data()
            ?: throw Exception("Could not find page data")

        val base64Data = LA_REGEX.find(scriptContent)?.groupValues?.get(1)
            ?: throw Exception("Could not extract base64 data")

        val decodedString = String(Base64.decode(base64Data, Base64.DEFAULT))

        return decodedString.split(",").mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    companion object {
        private val LA_REGEX = Regex("""la\s*=\s*'([A-Za-z0-9+/=]+)'""")
    }
}
