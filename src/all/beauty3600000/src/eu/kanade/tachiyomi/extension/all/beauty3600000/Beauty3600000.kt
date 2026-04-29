package eu.kanade.tachiyomi.extension.all.beauty3600000

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
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

    private val searchingClient: OkHttpClient = client.newBuilder()
        .rateLimit(1, 30)
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val request = searchMangaRequest(page, query, filters)
        val requestUrl = request.url
        val searchParams = requestUrl.queryParameter("search")
        return Observable.fromCallable {
            if (searchParams != null) {
                searchingClient.newCall(request).execute()
            } else {
                client.newCall(request).execute()
            }
        }.map { searchMangaParse(it) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queryUrl = query.toHttpUrlOrNull()
        if (queryUrl != null && queryUrl.host == baseUrl.toHttpUrlOrNull()?.host) {
            val id = queryUrl.queryParameter("p")?.trim()
            val slug = if (id == null) queryUrl.pathSegments.lastOrNull { it.isNotBlank() }?.removeSuffix(".html") else null
            val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegments(API_BASE)
                .addPathSegment("posts")
                .apply {
                    if (id != null) {
                        id.toIntOrNull()?.let { addQueryParameter("include", it.toString()) }
                            // Allow copy old entry's `manga.url` to search for old entry for migration which is in format "https://3600000.xyz/?p=/{slug}/"
                            ?: addQueryParameter("slug", id.removeSurrounding("/"))
                    } else if (slug != null) {
                        addQueryParameter("slug", slug)
                    }
                }.build()
            return GET(url, headers)
        }

        val filterList = filters.ifEmpty { getFilterList() }
        val categoryFilter = filterList.firstInstance<CategoryFilter>()
        val tagFilter = filterList.firstInstance<TagFilter>()
        var tagSearch: Int? = null

        if (categoryFilter.state <= 0 && tagFilter.state <= 0) {
            if (query.isBlank()) {
                return popularMangaRequest(page)
            }

            val tags = runCatching { runBlocking { getTag(query.trim()) } }.getOrNull()
            tagSearch = tags?.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }?.id
        }

        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegments(API_BASE)
            .addPathSegment("posts")
            .apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("per_page", PER_PAGE.toString())

                if (query.isNotBlank() && tagSearch == null) {
                    addQueryParameter("search", query.trim())
                }

                when {
                    categoryFilter.state > 0 -> addQueryParameter("categories", categoryFilter.toUriPart())
                    tagFilter.state > 0 -> addQueryParameter("tags", tagFilter.toUriPart())
                    tagSearch != null -> addQueryParameter("tags", tagSearch.toString())
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

    override fun mangaDetailsRequest(manga: SManga) = GET(
        baseUrl.toHttpUrlOrNull()!!.newBuilder().apply {
            addPathSegments(API_BASE)
            addPathSegment("posts")
            manga.url.toIntOrNull()?.let { addPathSegment(manga.url) }
                ?: addQueryParameter("slug", manga.url.removeSurrounding("/"))
        }
            .build(),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val post = response.toPost()

        return post.toSManga().apply {
            genre = runBlocking {
                listOf("categories", "tags").parallelCatchingFlatMap { term ->
                    getTerms(post.id, term)
                }
            }.takeIf { it.isNotEmpty() }
                ?.joinToString { it.name }
        }
    }

    private suspend fun getTerms(mangaId: Int, term: String): List<TermDto> {
        val request = GET(
            baseUrl.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegments(API_BASE)
                .addPathSegment(term)
                .addQueryParameter("post", mangaId.toString())
                .build(),
            headers,
        )
        return client.newCall(request).awaitSuccess()
            .parseAs<List<TermDto>>()
    }

    private suspend fun getTag(slug: String): List<TermDto> {
        val request = GET(
            baseUrl.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegments(API_BASE)
                .addPathSegment("tags")
                .addQueryParameter("slug", slug)
                .build(),
            headers,
        )
        return client.newCall(request).awaitSuccess()
            .parseAs<List<TermDto>>()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaId = manga.url.toIntOrNull() ?: run {
            val request = mangaDetailsRequest(manga)
            client.newCall(request).awaitSuccess()
                .use { mangaDetailsParse(it) }
                .url.toIntOrNull()
                ?: return emptyList()
        }
        val tags = getTerms(mangaId, "tags")
            .sortedBy { it.name.startsWith('[') }

        return tags.parallelCatchingFlatMap { tag ->
            val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegments(API_BASE)
                .addPathSegment("posts")
                .apply {
                    addQueryParameter("page", "1")
                    addQueryParameter("per_page", PER_PAGE.toString())
                    addQueryParameter("tags", tag.id.toString())
                }.build()

            client.newCall(GET(url, headers)).awaitSuccess()
                .use { searchMangaParse(it) }.mangas
        }
    }

    override fun getMangaUrl(manga: SManga): String = manga.url.toIntOrNull()
        ?.let { "$baseUrl/?p=${manga.url}" }
        ?: "$baseUrl${manga.url}"

    private fun Response.toPost(): PostDto {
        val slugParam = request.url.queryParameter("slug")
        return if (slugParam != null) {
            val body = body.string()
            jsonArrayRegex.find(body)
                ?.value
                ?.parseAs<List<PostDto>>()
                ?.firstOrNull()
                ?: throw IllegalArgumentException("Post not found")
        } else {
            parseAs<PostDto>()
        }
    }

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val post = response.toPost()
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
        val body = response.body.string()
        val posts = jsonArrayRegex.find(body)
            ?.value
            ?.parseAs<List<PostDto>>()
            ?: return MangasPage(emptyList(), false)
        val mangas = posts.map { it.toSManga() }
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(mangas, currentPage < totalPages)
    }

    /**
     * Parallel implementation of [Iterable.flatMap], but running
     * the transformation function inside a try-catch block.
     */
    private suspend inline fun <A, B> Iterable<A>.parallelCatchingFlatMap(crossinline f: suspend (A) -> Iterable<B>): List<B> = withContext(Dispatchers.IO) {
        map {
            async {
                try {
                    f(it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    // disable suggested mangas on Komikku
    override val disableRelatedMangasBySearch = true

    companion object {
        private const val API_BASE = "wp-json/wp/v2"
        private const val PER_PAGE = 100
        private val jsonArrayRegex by lazy { Regex("""\[.*]\s*$""") }

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
