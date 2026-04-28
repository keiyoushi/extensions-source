package eu.kanade.tachiyomi.extension.all.beauty3600000

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Beauty3600000 : HttpSource() {

    override val baseUrl = "https://3600000.xyz"

    override val lang = "all"

    override val name = "3600000 Beauty"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegments(API_BASE)
            .addPathSegment("posts")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response)

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queryUrl = query.toHttpUrlOrNull()
        if (queryUrl != null && queryUrl.host == baseUrl.toHttpUrlOrNull()?.host) {
            val id = queryUrl.queryParameter("p")
            val slug = if (id == null) queryUrl.pathSegments.lastOrNull { it.isNotBlank() }?.removeSuffix(".html") else null
            val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegments(API_BASE)
                .addPathSegment("posts")
                .apply {
                    if (id != null) {
                        addQueryParameter("include", id)
                    } else if (slug != null) {
                        addQueryParameter("slug", slug)
                    }
                }.build()
            return GET(url, headers)
        }

        val filterList = filters.ifEmpty { getFilterList() }
        val categoryFilter = filterList.firstInstance<CategoryFilter>()
        val tagFilter = filterList.firstInstance<TagFilter>()

        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegments(API_BASE)
            .addPathSegment("posts")
            .apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("per_page", PER_PAGE.toString())

                when {
                    categoryFilter.state != 0 -> addQueryParameter("categories", categoryFilter.toUriPart())
                    tagFilter.state != 0 -> addQueryParameter("tags", tagFilter.toUriPart())
                }
            }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Filters =========================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    // ========================= Details =========================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(
        baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegments(API_BASE)
            .addPathSegment("posts")
            .addPathSegment(manga.url)
            .build(),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<PostDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/?p=${manga.url}"

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val post = response.parseAs<PostDto>()
        return listOf(
            post.toSChapter().apply {
                date_upload = DATE_FORMAT.tryParse(post.date)
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/?p=${chapter.url}"

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = GET(
        baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegments(API_BASE)
            .addPathSegment("posts")
            .addPathSegment(chapter.url)
            .build(),
        headers,
    )

    override fun pageListParse(response: Response): List<Page> {
        val post = response.parseAs<PostDto>()
        val document = Jsoup.parseBodyFragment(post.content.rendered)
        return document.select("img").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Helpers =========================

    private fun parseMangasPage(response: Response): MangasPage {
        val posts = response.parseAs<List<PostDto>>()
        val mangas = posts.map { it.toSManga() }
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(mangas, currentPage < totalPages)
    }

    // disable suggested mangas on Komikku
    // site doesn't support keyword search and too slow
    override val disableRelatedMangasBySearch = true
    override val supportsRelatedMangas = false

    companion object {
        private const val API_BASE = "wp-json/wp/v2"
        private const val PER_PAGE = 100

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
