package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class CapibaraTraductor : KeiSource() {

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrlHost }

    private fun getScanHeaders(organizationSlug: String): Headers = headers.newBuilder()
        .add("x-organization", organizationSlug)
        .apply {
            authToken()?.let { set("Authorization", "Bearer $it") }
        }
        .build()

    private fun authToken(): String? = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name == "token" }
        ?.value
        ?.takeUnless { it.isBlank() || it.equals("deleted", ignoreCase = true) }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=popular".toHttpUrl(), page, headers)

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=latest".toHttpUrl(), page, headers)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/manga-custom".toHttpUrl().newBuilder()

        url.setQueryParameter("page", page.toString())
        url.setQueryParameter("limit", PAGE_LIMIT.toString())

        val headers = headers.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> url.setQueryParameter("order", filter.toUriPart())
                is ScanlatorFilter -> if (filter.state != 0) headers["x-organization"] = filter.toUriPart()
                else -> {}
            }
        }

        if (query.isNotBlank()) url.setQueryParameter("search", query)

        return parseMangaList(url.build(), page, headers.build())
    }

    private suspend fun parseMangaList(url: HttpUrl, page: Int, headers: Headers): MangasPage {
        val result = client.get(url, headers).parseAs<Data<SeriesListDataDto>>()

        val mangas = result.data.series.map { it.toSManga() }
        val hasNextPage = page < result.data.maxPage

        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val sfwScans = fetchAllScans(includeNsfw = false)
        val nsfwScans = fetchAllScans(includeNsfw = true)

        return buildList {
            add("Todos" to "")
            addAll(
                (sfwScans + nsfwScans)
                    .distinctBy { it.second }
                    .sortedBy { it.first },
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        data?.parseAs<List<Pair<String, String>>>()?.also {
            filters.add(ScanlatorFilter("Scanlator", it.toTypedArray()))
        }

        filters.add(SortByFilter("Ordenar por", getSortList()))

        return FilterList(filters)
    }

    private fun getSortList() = arrayOf(
        Pair("Recientes", "latest"),
        Pair("Popularidad", "popular"),
        Pair("A-Z", "alphabetical"),
    )

    private suspend fun fetchAllScans(includeNsfw: Boolean): List<Pair<String, String>> {
        val scans = mutableListOf<Pair<String, String>>()
        var page = 1

        while (true) {
            val url = "$baseUrl/api/landing/scans".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "name")
                .addQueryParameter("limit", "100")
                .apply {
                    if (includeNsfw) {
                        addQueryParameter("includeNSFW", "true")
                    }
                }
                .build()

            val response = client.get(url)
                .parseAs<Data<ScanListDto>>()
                .data

            scans += response.items.map { it.name to it.id }

            if (!response.hasNextPage()) {
                break
            }

            page++
        }

        return scans
    }

    override fun getMangaUrl(manga: SManga): String {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        return "$baseUrl/$organizationSlug/manga/$seriesSlug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        val result = client.get("$baseUrl/api/manga-custom/$seriesSlug", getScanHeaders(organizationSlug))
            .parseData<SeriesDto>()

        val chapterList = result.chapters
            ?.filter { it.isUnreleased.not() }
            ?.map { it.toSChapter(result.manga.slug, result.organization.slug) }
            ?.filter { it.date_upload < System.currentTimeMillis() }
            ?: emptyList()

        return SMangaUpdate(result.toSMangaDetails(), chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        return "$baseUrl/$organizationSlug/manga/$seriesSlug/chapters/$chapterSlug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        return client.get("$baseUrl/api/manga-custom/$seriesSlug/chapter/$chapterSlug/pages", getScanHeaders(organizationSlug))
            .parseData<List<PageDto>>()
            .mapNotNull { page ->
                val url = baseUrl.toHttpUrl().resolve(page.imageUrl) ?: return@mapNotNull null
                if (url.scheme != "http" && url.scheme != "https") return@mapNotNull null
                if ("promo" in url.pathSegments) return@mapNotNull null
                // Legacy files host does not resolve. Pages on the content CDN stay in the list.
                if (url.host == "files.capibaratraductor.com") return@mapNotNull null
                url.toString()
            }
            .mapIndexed { i, imageUrl ->
                Page(i, imageUrl = imageUrl)
            }
    }

    private inline fun <reified T> Response.parseData(): T {
        val root = parseAs<JsonElement>()

        if (root is JsonObject) {
            val data = root["data"]
            if (data != null && data !is JsonNull) {
                return data.parseAs()
            }
            if (T::class == SeriesDto::class && "manga" in root && "title" in root && "organization" in root) {
                return root.parseAs()
            }
        }

        if (T::class == List::class &&
            root is JsonArray &&
            root.all { it is JsonObject && "imageUrl" in it }
        ) {
            return root.parseAs()
        }

        val obj = root as? JsonObject
        if (obj?.get("errorType").apiText() == "login_required") {
            throw IOException("Log in via WebView to read this chapter.")
        }
        val message = obj?.get("message").apiText() ?: obj?.get("error").apiText()
        throw IOException(message ?: "Error")
    }

    private fun JsonElement?.apiText(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PAGE_LIMIT = 36
    }
}
