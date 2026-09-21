package eu.kanade.tachiyomi.extension.id.kumopoi

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.Random
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

@Source
abstract class KumoPoi : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(4, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = fetchComics(page = page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchComics(page = page, sort = "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var sort = "latest"
        var type = ""
        var status = ""
        val genres = mutableListOf<String>()

        for (filter in filters) {
            when (filter) {
                is SortFilter -> sort = filter.selected
                is TypeFilter -> type = filter.selected
                is StatusFilter -> status = filter.selected
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { genres.add(it.slug) }
                }
                else -> {}
            }
        }

        return fetchComics(
            page = page,
            search = query.trim(),
            sort = sort,
            type = type,
            status = status,
            genres = genres,
        )
    }

    private suspend fun fetchComics(
        page: Int,
        search: String = "",
        sort: String = "latest",
        type: String = "",
        status: String = "",
        genres: List<String> = emptyList(),
    ): MangasPage {
        val url = "$API_BASE/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_LIMIT.toString())
            if (sort.isNotBlank()) {
                addQueryParameter("sort", sort)
            }
            if (search.isNotBlank()) {
                addQueryParameter("search", search)
            }
            if (type.isNotBlank()) {
                addQueryParameter("type", type)
            }
            if (status.isNotBlank()) {
                addQueryParameter("status", status)
            }
            if (genres.isNotEmpty()) {
                addQueryParameter("genre", genres.joinToString(","))
            }
        }.build()

        val response = client.get(url).parseAs<ComicListResponse>()
        return MangasPage(response.items.map { it.toSManga() }, response.hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = getComicSlug(manga.url)
        val response = client.get("$API_BASE/comics/$slug").parseAs<ComicDetailsResponse>()
        return SMangaUpdate(response.data.toSManga(), response.data.toSChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!chapter.url.contains("#")) {
            throw Exception("Please refresh chapter list")
        }
        val chapterId = chapter.url.substringAfterLast("#")
        val path = "/api/v1/chapters/$chapterId/pages"
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = generateNonce(16)
        val signature = generateSignature(path, timestamp, nonce)

        val pageHeaders = headersBuilder()
            .add("x-app-timestamp", timestamp)
            .add("x-app-nonce", nonce)
            .add("x-app-signature", signature)
            .build()

        val response = client.get("$API_BASE_HOST$path", pageHeaders).parseAs<PagesResponse>()
        if (response.data.locked) {
            throw Exception("Chapter terkunci (Premium)")
        }

        return response.data.pages.mapIndexed { index, pageItem ->
            val deliverUrl = "$API_BASE/media/chapter/deliver?token=" +
                URLEncoder.encode(pageItem.token, "UTF-8")
            Page(index, imageUrl = deliverUrl)
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = getComicSlug(manga.url)
        return "$baseUrl/comic/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url.substringBeforeLast("#")}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segment = url.pathSegments.getOrNull(0) ?: return null
        if (segment != "comic" && segment != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = "/comic/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun getComicSlug(url: String): String = url.trimEnd('/').substringAfterLast('/')

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private fun generateNonce(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val random = Random()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateSignature(path: String, timestamp: String, nonce: String): String {
        val message = "GET:$path:$timestamp:$nonce"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(SIGNING_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val API_BASE_HOST = "https://api.kumopoi.com"
        private const val API_BASE = "https://api.kumopoi.com/api/v1"
        private const val SIGNING_KEY = "vtm6RLiSyKmWd1YpZtkt5ue4oPdYdmzW0ZgxDgyBNEM="
        private const val PAGE_LIMIT = 20
    }
}
