package eu.kanade.tachiyomi.extension.th.mikudoujin

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.minutes

@Source
abstract class MikuDoujin : KeiSource() {

    override val supportsLatest get() = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
        connectTimeout(1.minutes)
        readTimeout(1.minutes)
        writeTimeout(1.minutes)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/?page=$page").asJsoup()
        return mangaListParse(document)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val form = FormBody.Builder()
                .add("keysearch", query.trim())
                .build()
            val document = client.post("$baseUrl/controller/search/general/", body = form).asJsoup()
            return mangaListParse(document)
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        if (!genre.isNullOrEmpty()) {
            val document = client.get("$baseUrl/$genre/?page=$page").asJsoup()
            return mangaListParse(document)
        }

        return getPopularManga(page)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("div.col-6.inz-col").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                title = a.selectFirst("div.inz-title")?.text() ?: throw Exception("Missing title")
                thumbnail_url = a.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = mangas.isNotEmpty() &&
            document.selectFirst("button.btn-secondary:contains(Older)")?.let { !it.hasAttr("disabled") } == true

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document).apply { url = manga.url },
            chapterListParse(document, manga.url),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true)) return null

        val slug = url.pathSegments.firstOrNull().orEmpty()
        if (slug.isEmpty() || slug in EXCLUDED_PATHS) return null

        val document = client.get(url).asJsoup()
        if (document.selectFirst("div.sr-card-body") == null) return null

        return mangaDetailsParse(document).apply {
            this.url = url.encodedPath
            initialized = true
        }
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.selectFirst("div.sr-card-body") ?: throw Exception("Details not found")

        title = document.title().ifEmpty { throw Exception("Missing title") }
        author = infoElement.select("div.col-md-8 p a.badge-secondary").getOrNull(2)?.ownText()
        artist = author
        genre = infoElement.select("div.col-md-8 div.tags a").joinToString { it.text() }
        description = infoElement.selectFirst("div.col-md-8")?.ownText()
        thumbnail_url = infoElement.selectFirst("div.col-md-4 img")?.attr("abs:src")

        val tableEpisodes = document.select("table.table-episode tr td a")
        status = if (tableEpisodes.isEmpty()) {
            SManga.COMPLETED
        } else {
            val hasEnd = tableEpisodes.any { it.text().split(" ").last() == "จบ" }
            if (hasEnd) SManga.COMPLETED else SManga.UNKNOWN
        }

        initialized = true
    }

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document, mangaUrl: String): List<SChapter> {
        val elements = document.select("table.table-episode tr")

        if (elements.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    url = mangaUrl
                    name = "Chapter 1"
                    chapter_number = 1.0f
                },
            )
        }

        return elements.mapIndexedNotNull { idx, element ->
            val a = element.selectFirst("td a") ?: return@mapIndexedNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(a.attr("href"))
                name = a.text()
                chapter_number = if (name.isEmpty()) {
                    0.0f
                } else {
                    val lastWord = name.split(" ").last()
                    lastWord.toFloatOrNull() ?: (idx + 1).toFloat()
                }
            }
        }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val query = "div#v-pills-tabContent img.lazy, div#v-pills-tabContent img.page-img"
        return document.select(query).mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Text search ignores filters"),
        GenreFilter(),
    )

    companion object {
        private val EXCLUDED_PATHS = setOf("genre", "category", "member", "controller", "assets", "uploads", "search", "api")
    }
}
