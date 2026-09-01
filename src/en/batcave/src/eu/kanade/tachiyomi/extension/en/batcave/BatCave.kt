package eu.kanade.tachiyomi.extension.en.batcave

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Source
abstract class BatCave : KeiSource() {

    // Use client to sync cookies with WebView and intercept the DLE Guard redirect.
    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(DleGuardResolver.interceptor(baseUrl))
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Sec-Fetch-Dest", "document")
        set("Sec-Fetch-Mode", "navigate")
        set("Sec-Fetch-Site", "none")
        set("Sec-Fetch-User", "?1")
    }

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("comix")
            if (page > 1) {
                addPathSegments("page/$page")
            }
            addPathSegment("")
        }.build()

        val body = FormBody.Builder()
            .add("dlenewssortby", "rating")
            .add("dledirection", "desc")
            .add("set_new_sort", "dle_sort_cat_1")
            .add("set_direction_sort", "dle_direction_cat_1")
            .build()

        return parseSearchMangas(client.post(url, body))
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegments("page/$page")
                addPathSegment("")
            }
        }.build()

        val document = client.get(url).asJsoup()

        val entries = document.select("#content-load > .latest.grid-item").mapNotNull { element ->
            SManga.create().apply {
                with(element.selectFirst(".latest__title > a")!!) {
                    if (ownText().isEmpty()) return@mapNotNull null
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText()
                }
                thumbnail_url = element.selectFirst(".latest__img img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("li.pagination a[href]") != null

        return MangasPage(entries, hasNextPage)
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addPathSegment(query)
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                    addPathSegment("")
                }
            }.build()
            return parseSearchMangas(client.get(url))
        }

        var filtersApplied = false
        val filterPath = buildString {
            filters.firstInstanceOrNull<YearFilter>()?.takeIf { it.addFilterToUrl(this) }?.let { filtersApplied = true }
            filters.firstInstanceOrNull<PublisherFilter>()?.takeIf { it.addFilterToUrl(this) }?.let { filtersApplied = true }
            filters.firstInstanceOrNull<GenreFilter>()?.takeIf { it.addFilterToUrl(this) }?.let { filtersApplied = true }
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("ComicList")

            if (filtersApplied) {
                addPathSegments(filterPath)
            } else {
                // Send something to force ComicList
                addPathSegment("y[from]=1926")
                addPathSegment("y[to]=${Calendar.getInstance().get(Calendar.YEAR)}")
            }
            if (page > 1) {
                addPathSegments("page/$page")
            }
            addPathSegment("")
        }.build()

        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()

        val body = FormBody.Builder()
            .add("dlenewssortby", sort.getSort())
            .add("dledirection", sort.getDirection())
            .add("set_new_sort", "dle_sort_xfilter")
            .add("set_direction_sort", "dle_direction_xfilter")
            .build()

        return parseSearchMangas(client.post(url, body))
    }

    private fun parseSearchMangas(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("#dle-content > .readed").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".readed__title > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText()
                }
                thumbnail_url = element.selectFirst(".readed__img img")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"

        return MangasPage(entries, hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        url.pathSegments.firstOrNull() ?: return null
        return parseMangaDetails(client.get(url).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(doc), parseChapterList(doc))
    }

    private fun parseMangaDetails(doc: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst("header.page__header h1")!!.text()
        thumbnail_url = doc.selectFirst("div.page__poster img")?.absUrl("src")

        description = buildString {
            doc.getPageListItem("Publisher")?.let {
                append(it)
            }
            doc.getPageListItem("Year")?.let {
                appendLine(" — $it")
            }
            appendLine()
            append(doc.selectFirst("div.page__text")?.text())
        }

        author = doc.getPageListItem("Writer")
        artist = doc.getPageListItem("Artist")

        genre = buildList {
            doc.select("div.page__tags a").mapTo(this) { it.text() }
            add("Comic")
        }.joinToString()
        status = when (doc.selectFirst(".page__list > li:has(> div:contains(Release type))")?.ownText()?.trim()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        doc.selectFirst(".page__similar-panel.is-active")?.select(".scroller-2 a.poster")?.let { anchor ->
            memo = buildJsonObject {
                val similar = anchor.mapNotNull {
                    val title = it.selectFirst(".poster__title")?.text()?.takeIf { str -> str.isNotBlank() }
                    val url = it.attr("href").takeIf { str -> str.isNotBlank() }
                    val thumb = it.selectFirst("img")?.attr("data-src")
                    if (title != null && url != null) RelatedComic(title, url, thumb) else null
                }

                put("similarComics", similar.toJsonElement())
            }
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: error("Chapter data script not found")

        val data = script
            .substringAfter("window.__DATA__ = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<Chapters>()

        return data.chapters.map { chap ->
            SChapter.create().apply {
                url = "/reader/${data.comicId}/${chap.id}${data.xhash}"
                name = chap.title
                chapter_number = chap.number
                date_upload = dateFormat.tryParseDate(chap.date)
            }
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("d.M.yyyy")

    // ============================== Related ==============================
    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val related = manga.memo["similarComics"]

        return related?.parseAs<List<RelatedComic>>()?.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = "$baseUrl${it.thumbnail}"
                setUrlWithoutDomain(it.url)
            }
        } ?: emptyList()
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (newsId, rawId) = chapter.url.substringAfter("reader/").split("/", limit = 2)
        val id = intRegex.find(rawId)?.value ?: rawId
        val body = ChapterRequestBody(
            newsId = newsId,
            chapterId = id,
        ).toJsonRequestBody()

        val response = client.post("$baseUrl/engine/ajax/controller.php?mod=api&action=reader/getChapterData", body)
        val data = response.parseAs<ChapterApiResponse>().data
        return data.images.mapIndexed { idx, img ->
            val imageUrl = if (img.startsWith("http")) img.trim() else baseUrl + img.trim()
            Page(idx, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ==============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val doc = client.get("$baseUrl/comix/").asJsoup()
        val script = doc.selectFirst("script:containsData(window.__XFILTER__)")?.data()
            ?: error("Filter data not found")

        val rawFilters = script
            .substringAfter("window.__XFILTER__ = ")
            .substringBeforeLast(";")
            .trim()

        return rawFilters.parseAs<XFilters>().filterItems.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<XFilterItems>()

        val filters = mutableListOf(
            Filter.Header("Filters don't work with text search"),
            Filter.Separator(),
            SortFilter(),
            YearFilter(),
        )

        val publishers = filterData?.publisher?.values?.map { it.value to it.id } ?: emptyList()
        val genres = filterData?.genre?.values?.map { it.value to it.id } ?: emptyList()

        if (publishers.isNotEmpty()) {
            filters.add(PublisherFilter(publishers))
        }
        if (genres.isNotEmpty()) {
            filters.add(GenreFilter(genres))
        }
        return FilterList(filters)
    }

    fun Document.getPageListItem(label: String): String? = selectFirst(".page__list > li:has(> div:contains($label))")
        ?.selectFirst("> a")
        ?.text()
        ?.takeIf { it.isNotBlank() }

    companion object {
        private val intRegex = Regex("""^\d+""")
    }
}
