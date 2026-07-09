package eu.kanade.tachiyomi.extension.ja.klto9

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class Klto9 : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("sort_type", "DESC")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.thumb-item-flow").mapNotNull { element ->
            val a = element.selectFirst("div.thumb_attr.series-title a") ?: element.selectFirst("a")
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                title = a.text()

                val imgContainer = element.selectFirst("div.content.img-in-ratio")
                thumbnail_url = imgContainer?.attr("data-bg")?.takeIf { it.isNotEmpty() }
                    ?: imgContainer?.style()
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li a:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "last_update")
            .addQueryParameter("sort_type", "DESC")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("h3[style*=font-weight:bold]").text().ifEmpty {
                document.select("ol.breadcrumb li[itemprop=itemListElement]:last-child span").text()
            }

            val infoUl = document.selectFirst("ul.manga-info") ?: return this

            author = authorRegex.find(infoUl.html())?.groupValues?.get(1) ?: ""

            val otherNamesLi = infoUl.selectFirst("li:contains(Other name)")
            description = buildString {
                if (otherNamesLi != null) {
                    val names = otherNamesLi.text().substringAfter(":").trim()
                    append("Other Names: ").append(names).append("\n\n")
                }

                val descDiv = document.selectFirst("div.row:has(h3:contains(Description))")
                    ?: document.selectFirst("div.row:contains(Description)")
                val descText = descDiv?.select("p")?.text()
                if (!descText.isNullOrEmpty()) {
                    append(descText)
                }
            }

            val genres = infoUl.select("li:contains(Genre) small a").map { it.text() }
            genre = genres.joinToString()

            val statusText = infoUl.select("li:contains(Status) a").text().lowercase()
            status = when {
                statusText.contains("ongoing") || statusText.contains("incomplete") -> SManga.ONGOING
                statusText.contains("complete") -> SManga.COMPLETED
                statusText.contains("pause") || statusText.contains("hiatus") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val img = document.selectFirst("div.info-cover img.thumbnail")
            thumbnail_url = img?.absUrl("src")

            initialized = true
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("teap-").substringBefore(".html")
        val lstFilename = "${getRandomString(25)}.lst"
        val url = "$baseUrl/$lstFilename".toHttpUrl().newBuilder()
            .addQueryParameter("manga", slug)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<Dto>>().map { it.toSChapter() }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("#")
        return if (parts.size >= 3) {
            val chapterNum = parts[2].removeSuffix(".0")
            "$baseUrl/zmqs-${parts[1]}-chapter-$chapterNum.html"
        } else {
            baseUrl + chapter.url
        }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val cid = if (chapter.url.contains("#")) {
            chapter.url.substringBefore("#")
        } else {
            val request = GET(baseUrl + chapter.url, headers)
            val document = client.newCall(request).execute().use { it.asJsoup() }

            document.selectFirst("input#chapter")?.attr("value")
                ?: imageLoadRegex.find(document.html())?.groupValues?.get(1)
                ?: throw Exception("Could not find chapter ID (cid) in fallback flow")
        }

        val iogFilename = "${getRandomString(30)}.iog"
        val url = "$baseUrl/$iogFilename".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img").mapIndexed { index, img ->
            val src = (
                img.attr("data-pagespeed-lazy-src").takeIf { it.isNotEmpty() }
                    ?: img.absUrl("src")
                ).trim()
            Page(index, imageUrl = src)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        GenreFilter(getGenresList()),
        StatusFilter(),
        AuthorFilter(),
        GroupFilter(),
        SortFilter(),
    )

    // ============================= Utilities =============================

    private val bgImageRegex = Regex("""url\(['"]?([^'"]+)['"]?\)""")
    private val authorRegex = Regex("""Author\(s\)</b>:\s*<small><a[^>]*>([^<]+)""")
    private val imageLoadRegex = Regex("""load_image\((\d+)""")

    private fun Element.style(): String? {
        val style = attr("style")
        val match = bgImageRegex.find(style)
        return match?.groupValues?.get(1)
    }

    private fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
