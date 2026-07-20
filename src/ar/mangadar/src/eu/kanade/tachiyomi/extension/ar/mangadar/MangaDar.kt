package eu.kanade.tachiyomi.extension.ar.mangadar

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class MangaDar : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga/page/$page/".toHttpUrl()
        val response = client.get(url)
        return parseMangaListPage(response)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "mangaverse_search")
                .addQueryParameter("q", query)
                .build()
            val response = client.get(url)
            return parseSearchJson(response)
        }

        val urlBuilder = "$baseUrl/manga/".toHttpUrl().newBuilder()

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val sort = sortFilter?.selected.orEmpty()
        val type = typeFilter?.selected.orEmpty()
        val status = statusFilter?.selected.orEmpty()
        val genre = genreFilter?.selected.orEmpty()

        if (page > 1) {
            urlBuilder.addPathSegment("page")
            urlBuilder.addPathSegment(page.toString())
        }

        if (sort.isNotEmpty()) urlBuilder.addQueryParameter("sort", sort)
        if (type.isNotEmpty()) urlBuilder.addQueryParameter("type", type)
        if (status.isNotEmpty()) urlBuilder.addQueryParameter("status", status)
        if (genre.isNotEmpty()) urlBuilder.addQueryParameter("genre", genre)

        val response = client.get(urlBuilder.build())
        return parseMangaListPage(response)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.last { it.isNotEmpty() }
        if (slug.isBlank() || slug == "page") return null
        val response = client.get(url)
        return parseMangaDetails(response)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = "$baseUrl/manga/${manga.url}".toHttpUrl()
        val response = client.get(mangaUrl)
        val doc = response.asJsoup()

        val mangaDetail = parseMangaDetailsFromDoc(doc).apply { url = manga.url }
        val chapterList = parseChaptersFromDoc(doc)

        return SMangaUpdate(mangaDetail, chapterList)
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val response = client.get(url)
        val doc = response.asJsoup()
        return doc.select(".reader-page img").mapIndexed { index, img ->
            val imageUrl = img.attr("src").ifBlank { img.attr("data-src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== URLs ================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Filters =============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter("الترتيب", sortingList),
        TypeFilter("النوع", typeList),
        StatusFilter("الحالة", statusList),
        GenreFilter("التصنيف", genreFilterList),
    )

    // ============================== Parsers ============================

    private fun parseSearchJson(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = result.data.map { item ->
            val slug = item.url.toHttpUrl().pathSegments.last { it.isNotEmpty() }
            SManga.create().apply {
                title = item.title
                thumbnail_url = item.cover
                url = slug
            }
        }
        return MangasPage(mangas, false)
    }

    private fun parseMangaListPage(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("a[href*=/manga/]").mapNotNull { element ->
            val slug = element.absUrl("href").toHttpUrl()
                .pathSegments.last { it.isNotEmpty() }
            if (slug.isBlank() || slug == "page") return@mapNotNull null
            val title = element.select("img").attr("alt")
                .ifBlank { element.select("h3, h4, .font-semibold").text() }
            if (title.isBlank()) return@mapNotNull null
            val thumbnail = element.select("img").let { img ->
                img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
            }
            SManga.create().apply {
                url = slug
                this.title = title
                thumbnail_url = thumbnail
            }
        }.distinctBy { it.url }
        val hasNextPage = doc.select("a.next, a[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaDetails(response: Response): SManga {
        val doc = response.asJsoup()
        return parseMangaDetailsFromDoc(doc)
    }

    private fun parseMangaDetailsFromDoc(doc: Document): SManga = SManga.create().apply {
        title = doc.select("h1").text()
        description = doc.select("meta[name=description]").attr("content")
            .ifBlank { doc.select("p.text-neutral-400").text() }
        val statusText = doc.text()
        status = when {
            statusText.contains("مستمر") -> SManga.ONGOING
            statusText.contains("مكتمل") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = doc.select("a[href*=/genre/]")
            .distinctBy { it.text() }
            .joinToString(", ") { it.text() }
        val infoRows = doc.select("aside div:has(> span)")
        infoRows.forEach { row ->
            val spans = row.select("span")
            if (spans.size >= 2) {
                val label = spans[0].text()
                val value = spans[1].text()
                when {
                    label.contains("المؤلف") -> author = value
                    label.contains("الرسام") -> artist = value
                }
            }
        }
        thumbnail_url = doc.select("meta[property=og:image]").attr("content")
    }

    private fun parseChaptersFromDoc(doc: Document): List<SChapter> {
        val chaptersContainer = doc.selectFirst("div[x-data]:containsData(chapters)")
        if (chaptersContainer != null) {
            val xData = chaptersContainer.attr("x-data")
            val chaptersStart = xData.indexOf("chapters:")
            if (chaptersStart != -1) {
                val jsonStart = xData.indexOf("[", chaptersStart)
                val jsonEnd = findMatchingBracket(xData, jsonStart)
                if (jsonStart != -1 && jsonEnd != -1) {
                    val chaptersJson = xData.substring(jsonStart, jsonEnd + 1)
                    val chapters = chaptersJson.parseAs<List<ChapterDto>>()
                    return chapters.map { it.toSChapter() }
                }
            }
        }
        return emptyList()
    }

    private fun findMatchingBracket(str: String, startIndex: Int): Int {
        if (startIndex < 0 || startIndex >= str.length) return -1
        var depth = 0
        for (i in startIndex until str.length) {
            when (str[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
