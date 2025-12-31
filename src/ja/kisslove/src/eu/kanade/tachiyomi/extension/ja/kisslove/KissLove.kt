package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class KissLove : HttpSource() {
    override val name = "KissLove"
    override val baseUrl = "https://klz9.com"
    override val lang = "ja"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().apply {
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("api/manga/trending-daily")
            .build()
        return GET(url, sigAppend())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<Manga>>()
        val mangas = result.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/${manga.url}.html"
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "36")
            .build()
        return GET(url, sigAppend())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<PagedManga>()
        val mangas = result.items.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPages
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/search")
            .addQueryParameter("q", query)
            .build()
        return GET(url, sigAppend())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/slug")
            .addPathSegment(manga.url)
            .build()
        return GET(url, sigAppend())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<Manga>()
        return result.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<Manga>()
        val slug = result.slug
        return result.chapters.map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterSuffix = chapter.url.substringAfterLast("/")
        return "$baseUrl/$chapterSuffix.html"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringBeforeLast("/")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/chapter")
            .addPathSegment(id)
            .build()
        return GET(url, sigAppend())
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<Chapter>()
        return result.content
            .lines()
            .filter { it.isNotBlank() }
            .mapIndexed { i, img ->
                Page(i, imageUrl = img)
            }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("筛选"),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun sigAppend(): Headers = headers.newBuilder().apply {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val payload = "$timestamp.$CLIENT_ID"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val signature = hashBytes.joinToString("") { byte ->
            "%02x".format(byte)
        }
        add("X-Client-Sig", signature)
        add("X-Client-Ts", timestamp)
    }.build()

    companion object {
        private const val CLIENT_ID = "KL9K40zaSyC9K40vOMLLbEcepIFBhUKXwELqxlwTEF"
        val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }
    }
}
