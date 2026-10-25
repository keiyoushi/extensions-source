package eu.kanade.tachiyomi.extension.en.oriyami

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class OriYami : KeiSource() {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    private val apiHeaders get() = headersBuilder().set("Accept", "application/json").build()

    override suspend fun getPopularManga(page: Int): MangasPage = fetchPagedList("popular-list", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchPagedList("recently-updated", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().addPathSegments("mangas/browse/").apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
            addQueryParameter("page", page.toString())
            addQueryParameter("page_size", PAGE_SIZE)
        }.build()
        val list = client.get(url, apiHeaders).parseAs<BrowseList>()
        return MangasPage(list.results.map { it.toSManga() }, list.next != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in TYPE_PATHS || segments[1].isEmpty()) return null
        return fetchDetails(segments[1]).toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${typePath(manga.memo)}/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = fetchDetails(manga.url)
        return SMangaUpdate(
            details.toSManga(),
            details.scans.reversed().map { it.toSChapter(details.slug, details.type) },
        )
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = fetchDetails(manga.url).relations.map { it.toSManga() }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${typePath(chapter.memo)}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegment("mangas")
            .addPathSegment(chapter.memo[MEMO_SLUG]!!.string)
            .addPathSegment("chapters")
            .addPathSegment(chapter.memo[MEMO_NUMBER]!!.string)
            .addPathSegment("")
            .build()
        return client.get(url, apiHeaders).parseAs<ChapterPages>().pages
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/library")
        .extractNextJs<LibraryData> { it is JsonObject && "initialMeta" in it && "initialYears" in it }!!
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val library = data?.parseAs<LibraryData>()
        return FilterList(
            buildList {
                add(SortFilter())
                add(AnimeFilter())
                if (library != null) {
                    add(TriStateGroup("Type", "type", library.initialMeta.types))
                    add(TriStateGroup("Status", "status", library.initialMeta.statuses))
                    add(TriStateGroup("Demographic", "demographic", library.initialMeta.demographics))
                    add(TriStateGroup("Release year", "years", library.initialYears.map { it.toString() }))
                    add(TriStateGroup("Genres", "genre", library.initialMeta.genres))
                }
            },
        )
    }

    private suspend fun fetchPagedList(path: String, page: Int): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("mangas/$path/")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", PAGE_SIZE)
            .build()
        val list = client.get(url, apiHeaders).parseAs<PagedList>()
        return MangasPage(list.results.map { it.toSManga() }, list.hasNext)
    }

    private suspend fun fetchDetails(slug: String): MangaDetails {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegment("mangas")
            .addPathSegment(slug)
            .addPathSegment("")
            .build()
        return client.get(url, apiHeaders).parseAs()
    }

    // the site routes every other type (Global-Manga) under /manga
    private fun typePath(memo: JsonObject): String = when (memo[MEMO_TYPE]?.stringOrNull) {
        "Manhwa" -> "manhwa"
        "Manhua" -> "manhua"
        "Digital Comic" -> "digital-comic"
        else -> "manga"
    }

    companion object {
        private const val API_URL = "https://api.oriyami.com/api"
        private const val PAGE_SIZE = "18"
        private val TYPE_PATHS = setOf("manga", "manhwa", "manhua", "digital-comic")
    }
}
