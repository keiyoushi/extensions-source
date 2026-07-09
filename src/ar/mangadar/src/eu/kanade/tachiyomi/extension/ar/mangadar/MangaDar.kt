package eu.kanade.tachiyomi.extension.ar.mangadar

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class MangaDar : HttpSource() {

    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "mangaverse_search")
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
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

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("application/json")) {
            return parseSearchJson(response)
        }
        return parseMangaListPage(response)
    }

    private fun parseSearchJson(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = result.data.map { item ->
            val encodedPath = item.url.toHttpUrl().encodedPath
            val slug = encodedPath.trim('/').substringAfterLast("manga/").trim('/')
            SManga.create().apply {
                title = item.title
                thumbnail_url = item.cover
                url = "/manga/$slug"
            }
        }
        return MangasPage(mangas, false)
    }

    private fun parseMangaListPage(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("a[href*=/manga/]").mapNotNull { element ->
            val href = element.absUrl("href")
            if (href.isBlank() || !href.contains("/manga/")) return@mapNotNull null
            val slug = href.removePrefix("$baseUrl/manga/").trim('/')
            if (slug.isBlank() || slug.contains("/") || slug == "page") return@mapNotNull null
            val title = element.select("img").attr("alt")
                .ifBlank { element.select("h3, h4, .font-semibold").text() }
            if (title.isBlank()) return@mapNotNull null
            val thumbnail = element.select("img").let { img ->
                img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
            }
            SManga.create().apply {
                url = "/manga/$slug"
                this.title = title
                thumbnail_url = thumbnail
            }
        }.distinctBy { it.url }
        val hasNextPage = doc.select("a.next, a[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
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
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val chaptersContainer = doc.select("div[x-data]").firstOrNull { element ->
            element.attr("x-data").contains("chapters")
        }
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

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select(".reader-page img").mapIndexed { index, img ->
            val url = img.attr("src").ifBlank { img.attr("data-src") }
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        SortFilter("الترتيب", sortingList),
        TypeFilter("النوع", typeList),
        StatusFilter("الحالة", statusList),
        GenreFilter("التصنيف", genreFilterList),
    )

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
