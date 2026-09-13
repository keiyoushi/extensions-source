package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Source
abstract class WestManga : KeiSource() {
    private val apiUrl = "https://data.mantweh.online"

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.popular)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.latest)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/api/contents".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("type", "Comic")
            filters.filterIsInstance<UrlFilter>().forEach {
                it.addToUrl(this)
            }
        }.build()

        val data = client.get(url, apiHeaders(url)).parseAs<PaginatedData<BrowseManga>>()
        val entries = data.data.map { it.toSManga() }
        return MangasPage(entries, data.paginator.hasNextPage())
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$apiUrl/api/contents/genres".toHttpUrl()
        return client.get(url, apiHeaders(url, includeToken = false)).parseAs()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Data<List<ApiGenre>>>()?.data
            ?.map { it.name to it.id.toString() }

        return FilterList(
            listOfNotNull(
                SortFilter(),
                StatusFilter(),
                CountryFilter(),
                ColorFilter(),
                genres?.takeIf { it.isNotEmpty() }?.let { GenreFilter(it) },
            ),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "comic") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val api = "$apiUrl/api/comic/$slug".toHttpUrl()

        return client.get(api, apiHeaders(api)).parseAs<Data<Manga>>().data
            .toSManga(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        require(path.size == 3) { "Migrate from $name to $name" }
        val slug = path[1]
        val api = "$apiUrl/api/comic/$slug".toHttpUrl()

        val data = client.get(api, apiHeaders(api)).parseAs<Data<Manga>>().data
        return SMangaUpdate(
            data.toSManga(baseUrl),
            data.chapters.map { it.toSChapter() },
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        return "$baseUrl/comic/$slug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        require(path.isNotEmpty()) { "Refresh Chapter List" }
        val slug = path.first()

        val url = "$apiUrl/api/v/$slug".toHttpUrl()
        val data = client.get(url, apiHeaders(url)).parseAs<Data<ImageList>>().data
        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.first()
        return "$baseUrl/view/$slug"
    }

    private var tokenCache: String? = null
    private var tokenChecked = false

    private suspend fun bearerToken(): String? {
        if (tokenChecked) return tokenCache
        tokenCache = runCatching { getLocalStorage(baseUrl, "access_token") }.getOrNull()?.takeIf { it.isNotBlank() }
        tokenChecked = true
        return tokenCache
    }

    private suspend fun apiHeaders(url: HttpUrl, includeToken: Boolean = true): Headers {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val message = "wm-api-request"
        val key = timestamp + "GET" + url.encodedPath + ACCESS_KEY + SECRET_KEY
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        return headersBuilder().apply {
            if (includeToken) {
                bearerToken()?.let { set("Authorization", "Bearer $it") }
            }
            set("x-wm-request-time", timestamp)
            set("x-wm-accses-key", ACCESS_KEY)
            set("x-wm-request-signature", signature)
        }.build()
    }
}

private const val ACCESS_KEY = "WM_WEB_FRONT_END"
private const val SECRET_KEY = "xxxoidj"
