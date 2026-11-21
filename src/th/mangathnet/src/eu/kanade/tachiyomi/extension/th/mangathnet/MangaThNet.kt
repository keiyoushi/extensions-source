package eu.kanade.tachiyomi.extension.th.mangathnet

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Collator
import java.util.Locale

@Source
abstract class MangaThNet : KeiSource() {

    private val rscHeaders: Headers
        get() = headersBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = getLibrary(sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getLibrary(sort = "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getLibrary(
        type = filters.firstInstanceOrNull<TypeFilter>()?.selected,
        query = query,
        status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
        tags = filters.firstInstanceOrNull<TagFilter>()?.selected.orEmpty(),
        sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: "popular",
    )

    // The library holds every entry of a type, and the site searches, filters, sorts and pages it in the browser
    private suspend fun getLibrary(
        type: String? = null,
        query: String = "",
        status: String? = null,
        tags: List<String> = emptyList(),
        sort: String = "popular",
    ): MangasPage {
        val text = query.trim().lowercase()
        val entries = getCatalog(type).filter { it.matches(text, status, tags) }
        val sorted = when (sort) {
            "updated" -> entries.sortedByDescending { it.updated }
            "rating" -> entries.sortedByDescending { it.rating }
            "bookmarked" -> entries.sortedByDescending { it.bookmarkCount }
            "title" -> entries.sortedWith(compareBy(TITLE_COLLATOR) { it.title })
            else -> entries.sortedByDescending { it.totalViews }
        }
        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    // Novels are text
    private suspend fun getCatalog(type: String?): List<LibraryEntryDto> {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("library").apply { type?.let(::addPathSegment) }.build()
        return client.get(url, rscHeaders, Fetch.XHR).extractNextJs<List<LibraryEntryDto>>()!!
            .filter { it.contentType in TYPES }
    }

    // Library links, with the search parameter the site declares; series and chapter links go to getMangaByUrl
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSite() || url.pathSegments[0] != "library") return super.getMangasByUrl(url, page)
        return getLibrary(
            type = url.pathSegments.getOrNull(1)?.takeIf { it in TYPES },
            query = url.queryParameter("q").orEmpty(),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.isSite()) return null
        val segment = url.pathSegments[0]
        val id = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val path = when (segment) {
            in TYPES -> "/$segment/$id"
            // Short chapter links, which the site redirects to the chapter page
            "chapter" -> getChapterData(id).takeIf { it.contentType in TYPES }?.path ?: return null
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The series page has the details and every chapter
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val series = document.extractNextJs<SeriesDto>()!!
        val info = document.selectFirst("script[type=application/ld+json]")!!.data().parseAs<JsonLdDto>().series
        val artist = document.selectFirst("span:containsOwn(นักวาด) + span")?.text()?.takeUnless { it == "-" }
        return SMangaUpdate(series.toSManga(manga.url, info, artist), series.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith("🔒")) return emptyList()
        return getChapterData(chapter.url.substringAfterLast('/')).toPages()
    }

    private suspend fun getChapterData(id: String): ChapterDataDto {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("api/chapters").addPathSegment(id).build()
        return client.get(url, Fetch.XHR.from("/chapter/$id")).parseAs<ChapterDataDto>()
    }

    override val supportsFilterFetching = true

    // The tags of the whole library, the most used first, as the site lists them
    override suspend fun fetchFilterData(): JsonElement = getCatalog(null)
        .flatMap { entry -> entry.tags.map { it.lowercase() } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<String>>())

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    companion object {
        private val TYPES = setOf("manga", "manhwa", "manhua", "doujin")
        private val TITLE_COLLATOR = Collator.getInstance(Locale.forLanguageTag("th"))
    }
}
