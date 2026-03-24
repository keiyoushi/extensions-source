package eu.kanade.tachiyomi.extension.en.manta

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MantaComics : HttpSource() {
    override val name = "Manta"

    override val lang = "en"

    override val baseUrl = "https://manta.net"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val cookies = client.cookieJar.loadForRequest(url)
            val token = cookies.find { it.name == "token" }?.value

            if (token != null) {
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun fetchPopularManga(page: Int) = latestUpdatesRequest(page).fetch(::searchMangaParse)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manta/v1/search/series?cat=New", headers)

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manta/v1/search/series".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            } else {
                val category = filters.category
                val selected = if (category.second.isEmpty()) "tagId=288" else category.second
                val (key, value) = selected.split("=")
                addQueryParameter(key, value)
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = response.parseAs<MantaResponse<List<Series<Title>>>>().data.map {
        SManga.create().apply {
            title = it.toString()
            url = it.id.toString()
            thumbnail_url = it.image.toString()
        }
    }.let { MangasPage(it, false) }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = searchMangaRequest(page, query, filters).fetch(::searchMangaParse)

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/front/v1/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val data = response.parseAs<MantaResponse<Series<Details>>>().data.data
        description = data.toString()
        genre = data.tags.joinToString()
        artist = data.artists.joinToString()
        author = data.authors.joinToString()
        status = when (data.isCompleted) {
            true -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        initialized = true
    }

    override fun fetchMangaDetails(manga: SManga) = mangaDetailsRequest(manga).fetch(::mangaDetailsParse)

    // ============================ Chapter List ============================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) = response.parseAs<MantaResponse<Series<Title>>>().data.episodes!!.map {
        SChapter.create().apply {
            name = it.toString()
            url = it.id.toString()
            date_upload = it.timestamp
            chapter_number = it.ord.toFloat()
        }
    }.reversed()

    override fun fetchChapterList(manga: SManga) = chapterListRequest(manga).fetch(::chapterListParse)

    // ============================= Page List ==============================

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/front/v1/episodes/${chapter.url}", headers)

    override fun pageListParse(response: Response) = response.parseAs<MantaResponse<Episode>>().data.cutImages?.mapIndexed { idx, img ->
        Page(idx, "", img.toString())
    } ?: emptyList()

    override fun fetchPageList(chapter: SChapter) = pageListRequest(chapter).fetch(::pageListParse)

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList() = FilterList(
        Filter.Header("Filters are ignored when searching"),
        Filter.Separator(),
        Category(),
    )

    // ============================= Utilities ==============================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/episodes/${chapter.url}"

    private fun <R> Request.fetch(parse: (Response) -> R) = client.newCall(this).asObservable().map { res ->
        if (res.isSuccessful) return@map parse(res)
        error(res.parseAs<MantaResponse<Unit>>().status.toString())
    }!!
}
