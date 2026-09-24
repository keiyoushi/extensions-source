package eu.kanade.tachiyomi.extension.ja.klto9

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Klto9 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("sort_type", "DESC")
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "last_update")
            .addQueryParameter("sort_type", "DESC")
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }

        var hasSort = false
        val genreInclude = mutableListOf<String>()
        val genreExclude = mutableListOf<String>()

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        genreFilter?.state?.forEach { state ->
            if (state.state == Filter.TriState.STATE_INCLUDE) genreInclude.add(state.name)
            if (state.state == Filter.TriState.STATE_EXCLUDE) genreExclude.add(state.name)
        }

        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        if (statusFilter != null && statusFilter.state != 0) {
            url.addQueryParameter("m_status", STATUS_VALUES[statusFilter.state].second)
        }

        val authorFilter = filters.firstInstanceOrNull<AuthorFilter>()
        if (authorFilter != null && authorFilter.state.isNotEmpty()) {
            url.addQueryParameter("author", authorFilter.state)
        }

        val groupFilter = filters.firstInstanceOrNull<GroupFilter>()
        if (groupFilter != null && groupFilter.state.isNotEmpty()) {
            url.addQueryParameter("group", groupFilter.state)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        if (sortFilter != null && sortFilter.state != null) {
            val selection = sortFilter.state!!
            url.addQueryParameter("sort", SORT_VALUES[selection.index].second)
            url.addQueryParameter("sort_type", if (selection.ascending) "ASC" else "DESC")
            hasSort = true
        }

        if (!hasSort) {
            url.addQueryParameter("sort", "last_update")
            url.addQueryParameter("sort_type", "DESC")
        }

        if (genreInclude.isNotEmpty()) {
            url.addQueryParameter("genre", genreInclude.joinToString(","))
        }
        if (genreExclude.isNotEmpty()) {
            url.addQueryParameter("ungenre", genreExclude.joinToString(","))
        }

        val document = client.get(url.build()).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("div.thumb-item-flow").mapNotNull { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("ul.pagination li a[href]:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val a = element.selectFirst("div.thumb_attr.series-title a") ?: element.selectFirst("a") ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(a.absUrl("href"))
            title = a.text()
            thumbnail_url = element.selectFirst("div.content.img-in-ratio")?.backgroundUrl()
        }
    }
    private fun Element.backgroundUrl(): String? = absUrl("data-bg").ifBlank {
        val styleUrl = bgImageRegex.find(attr("style"))?.groupValues?.get(1) ?: return null
        ownerDocument()?.baseUri()?.toHttpUrl()?.resolve(styleUrl)?.toString()
    }
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segment = url.pathSegments.singleOrNull() ?: return null
        val mangaPath = when {
            segment.startsWith("teap-") -> "/$segment"
            segment.startsWith("zmqs-") && segment.contains("-chapter-") ->
                segment
                    .substringAfter("zmqs-")
                    .substringBefore("-chapter-")
                    .ifEmpty { null }
                    ?.let { "/teap-$it.html" }
            else -> null
        } ?: return null

        return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(mangaPath) }).apply {
            setUrlWithoutDomain(mangaPath)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("ol.breadcrumb li[itemprop=itemListElement]:last-child span")!!.text()

            val infoUl = document.selectFirst("ul.manga-info") ?: return this

            author = authorRegex.find(infoUl.html())?.groupValues?.get(1)

            val otherNamesLi = infoUl.selectFirst("li:contains(Other name)")
            description = buildString {
                if (otherNamesLi != null) {
                    val names = otherNamesLi.text().substringAfter(":").trim()
                    append("Other Names: ").append(names).append("\n\n")
                }

                val descDiv = document.selectFirst("div.row:has(h3:contains(Description))")
                    ?: document.selectFirst("div.row:contains(Description)")
                descDiv?.selectFirst("p")
                    ?.textOrNull()
                    ?.takeUnless { it == "Updating" }
                    ?.also { append(it) }
            }

            genre = infoUl.select("li:contains(Genre) small a").joinToString { it.text() }.ifEmpty { null }

            status = parseStatus(infoUl.selectFirst("li:contains(Status) a")?.textOrNull())

            val img = document.selectFirst("div.info-cover img.thumbnail")
            thumbnail_url = img?.absUrl("src")

            initialized = true
        }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", ignoreCase = true) || status.contains("incomplete", ignoreCase = true) -> SManga.ONGOING
        status.contains("complete", ignoreCase = true) -> SManga.COMPLETED
        status.contains("pause", ignoreCase = true) || status.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.substringAfter("teap-").substringBefore(".html")
        val lstFilename = "${getRandomString(25)}.lst"
        val url = "$baseUrl/$lstFilename".toHttpUrl().newBuilder()
            .addQueryParameter("manga", slug)
            .build()

        return client.get(url).parseAs<List<Dto>>().map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("#")
        return if (parts.size >= 3) {
            val chapterNum = parts[2].removeSuffix(".0")
            "$baseUrl/zmqs-${parts[1]}-chapter-$chapterNum.html"
        } else {
            baseUrl + chapter.url
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val cid = if (chapter.url.contains("#")) {
            chapter.url.substringBefore("#")
        } else {
            val document = client.get(getChapterUrl(chapter)).asJsoup()
            document.selectFirst("input#chapter")?.attr("value")
                ?: imageLoadRegex.find(document.html())?.groupValues?.get(1)
                ?: throw Exception("Could not find chapter ID (cid) in fallback flow")
        }

        val iogFilename = "${getRandomString(30)}.iog"
        val url = "$baseUrl/$iogFilename".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val document = client.get(url).asJsoup()
        return document.select("img").mapIndexed { index, img ->
            val src = img.absUrl("data-pagespeed-lazy-src").ifEmpty { img.absUrl("src") }
            Page(index, imageUrl = src)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        GenreFilter(getGenresList()),
        StatusFilter(),
        AuthorFilter(),
        GroupFilter(),
        SortFilter(),
    )

    private val bgImageRegex = Regex("""url\(['"]?([^'"]+)['"]?\)""")
    private val authorRegex = Regex("""Author\(s\)</b>:\s*<small><a[^>]*>([^<]+)""")
    private val imageLoadRegex = Regex("""load_image\((\d+)""")

    private fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
