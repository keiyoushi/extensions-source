package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element

@Source
abstract class InfinityScans : KeiSource() {

    private val cdnHost = "cv.infinityscans.org"

    private val pageCdnHost = "ch.infinityscans.org"

    private var slugHash: String
        get() = preferences.getString(PREF_SLUG_HASH, DEFAULT_SLUG_HASH) ?: DEFAULT_SLUG_HASH
        set(value) = preferences.edit().putString(PREF_SLUG_HASH, value).apply()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(WebviewInterceptor(baseUrl))
        rateLimit(5)
    }

    private val rscHeaders
        get() = headersBuilder().set("rsc", "1").build()

    private val apiHeaders
        get() = headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Sec-Fetch-Dest", "empty")
            add("Sec-Fetch-Mode", "cors")
            add("Sec-Fetch-Site", "same-origin")
            add("X-requested-with", "XMLHttpRequest")
        }.build()

    private val preferences by getPreferencesLazy()

    // ============================== Popular + Latest ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, SortType.Popularity.value)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, SortType.Latest.value)

    private suspend fun getMangaList(page: Int, sort: String): MangasPage {
        val response = client.get("$baseUrl/api/comics?page=$page&sort=$sort", apiHeaders)
        val data = response.parseAs<SearchResultDto>()
        val entries = data.titles.map { it.toSManga(cdnHost) }
        return MangasPage(entries, entries.isNotEmpty())
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = if (query.isBlank()) {
            val url = "$baseUrl/api/comics".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                filters.forEach { filter ->
                    when (filter) {
                        is SortFilter -> filter.selected?.let { addQueryParameter("sort", it) }
                        is GenreFilter -> filter.checked?.let { genres ->
                            genres.forEach { addQueryParameter("genre", it) }
                        }
                        is AuthorFilter -> filter.checked?.let { authors ->
                            authors.forEach { addQueryParameter("author", it) }
                        }
                        is StatusFilter -> filter.checked.firstOrNull()?.let { addQueryParameter("status", it) }
                        else -> {}
                    }
                }
            }.build()
            client.get(url, apiHeaders)
        } else {
            val body = SearchRequestBody(search = query).toJsonRequestBody()
            client.post("$baseUrl/api/search", apiHeaders, body)
        }

        return if (query.isNotEmpty()) {
            val list = response.parseAs<ResponseDto<List<SearchEntryDto>>>().result
            val mangas = list.map { it.toSManga(cdnHost) }
            MangasPage(mangas, false)
        } else {
            val data = response.parseAs<SearchResultDto>()
            val mangas = data.titles.map { it.toSManga(cdnHost) }
            MangasPage(mangas, mangas.isNotEmpty())
        }
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val pathSegments = url.pathSegments
        if (pathSegments.size < 3) return null
        val id = pathSegments[1]
        val slug = pathSegments[2]

        val html = client.get(url, rscHeaders).body.string()
        return parseMangaDetails(html)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}-$slugHash"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val url = "$baseUrl/comic/${manga.url}-$slugHash"
        val html = client.get(url, rscHeaders).body.string()
        return runCatching { parseMangaDetails(html) }
            .getOrElse {
                val newHash = HASH_REGEX.find(html)?.groupValues?.get(1) ?: error("Failed to find slug hash")
                slugHash = newHash
                val newHtml = client.get("$baseUrl/comic/${manga.url}-$slugHash", rscHeaders).body.string()
                parseMangaDetails(newHtml)
            }
    }

    private fun parseMangaDetails(responseHtml: String): SManga {
        val dto = responseHtml.extractNextJsRsc<MangaDetailsDto>()!!
        return SManga.create().apply {
            url = dto.uri.substringAfter("/")
            title = dto.name
            description = buildString {
                dto.description.content
                    .firstOrNull()
                    ?.content
                    ?.joinToString(" ") { it.text }
                    .orEmpty()
                    .let { append(it) }

                dto.altNames?.takeIf { it.isNotEmpty() }?.let {
                    append("\n\nAlternative Title: ")
                    append(it.joinToString(" · "))
                }
            }
            author = dto.authors.joinToString { it.name }
            genre = dto.genres.joinToString { it.name }
            status = dto.status.parseStatus()
            thumbnail_url = "https://$cdnHost/${dto.cover}"
        }
    }

    private suspend fun getChapterList(manga: SManga) = coroutineScope {
        val id = manga.url.substringBefore("/")
        val first = client.get("$baseUrl/api/comic/$id/chapters?page=0").parseAs<ChapterListDto>()

        val all = first.chapters.toMutableList()
        if (first.total > first.chapters.size) {
            val chunkSize = first.chapters.size
            val totalPages = (first.total + chunkSize - 1) / chunkSize

            (1 until totalPages).map { page ->
                async {
                    client.get("$baseUrl/api/comic/$id/chapters?page=$page")
                        .parseAs<ChapterListDto>().chapters
                }
            }.awaitAll().forEach { all.addAll(it) }
        }
        all.map { it.toSChapter("comic/$id") }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('/')
        val mangaId = chapter.url.split("/")[1]

        val pageHeaders = headersBuilder()
            .set("next-action", "6059eb844d4cb2658ebbdc562485ac7f318a7c89cb")
            .build()

        val body = listOf(mangaId, chapterId).toJsonRequestBody()
        // Dummy slug for server to trigger empty set-cookie if expired, baseUrl alone doesn't.
        val pagesUrl = "$baseUrl/comic/$mangaId/dummy-slug/chapter/$chapterId"

        val response = client.post(pagesUrl, pageHeaders, body)
        val pages = response.extractNextJs<List<PageEntryDto>>()
        return pages?.mapIndexed { index, p ->
            Page(index, imageUrl = "https://$pageCdnHost/${p.path}")
        } ?: emptyList()
    }

    override fun imageRequest(page: Page): Request {
        val pageHeaders = headers.newBuilder().apply {
            add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()
        return GET(page.imageUrl!!, pageHeaders)
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        // load genre and author filters
        val response = client.get("$baseUrl/api/comics/options", apiHeaders)
        return response.parseAs<FiltersDto>().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val dto = data?.parseAs<FiltersDto>()

        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filtering is ignored when searching by text."),
            SortFilter(),
            StatusFilter(),
        )

        dto?.let {
            if (dto.genres.isNotEmpty()) filters += GenreFilter("Genres", dto.genres.map { it.name to it.id })
            if (dto.authors.isNotEmpty()) filters += AuthorFilter("Authors", dto.authors.map { it.name to it.id })
        }

        return FilterList(filters)
    }

    // ============================= Utilities =============================

    private fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(name: String): String? = selectFirst("div:has(>span:matches($name:))")?.ownText()

    private fun Element.getLinks(name: String): String? = select("div:has(>span:matches($name:)) a")
        .joinToString(transform = Element::text)
        .takeIf { it.isNotEmpty() }

    companion object {
        private const val DEFAULT_SLUG_HASH = "d806990541c8"
        private const val PREF_SLUG_HASH = "pref_slug_hash"
        private val HASH_REGEX = Regex("NEXT_REDIRECT.*https.+?-(\\w+);")
    }
}
