package eu.kanade.tachiyomi.extension.id.komikucc

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.UnsupportedOperationException
import kotlin.time.Duration.Companion.minutes

const val DOMAIN = "komiku.cc"
const val CDN_URL = "https://cdn.$DOMAIN/"

class Komikucc : HttpSource() {
    override val name = "Komiku.cc"
    override val lang = "id"
    override val baseUrl = "https://$DOMAIN"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(2.minutes)
        .readTimeout(2.minutes)
        .callTimeout(2.minutes)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotBlank()) {
            return if (query.startsWith("https://")) {
                deepLink(query)
            } else {
                querySearch(query)
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    private fun querySearch(query: String): Observable<MangasPage> {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()
        val request = GET(url, headers)

        return client.newCall(request)
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()

                val mangas = document.select("a[href*=/komik/]").map { element ->
                    SManga.create().apply {
                        this@apply.url = element.absUrl("href").toHttpUrl().pathSegments[1]
                        title = element.selectFirst("h3")!!.text()
                        thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    }
                }

                MangasPage(mangas, hasNextPage = false)
            }
    }

    private fun deepLink(link: String): Observable<MangasPage> {
        val url = link.toHttpUrl()
        if (url.host == DOMAIN && url.pathSegments[0] == "komik") {
            val slug = url.pathSegments[1]
            val tmpManga = SManga.create().apply {
                this@apply.url = slug
            }

            return fetchMangaDetails(tmpManga)
                .map { MangasPage(listOf(it), false) }
        }

        throw Exception("Unsupported url")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/list".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.status?.also { status ->
                        addQueryParameter("status", status)
                    }
                    is TypeFilter -> filter.type?.also { type ->
                        addQueryParameter("type", type)
                    }
                    is SortFilter -> filter.sort?.also { sort ->
                        addQueryParameter("order", sort)
                    }
                    is GenreFilter -> filter.checked.forEach { genre ->
                        addQueryParameter("genre[]", genre)
                    }
                    else -> {}
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, rscHeaders)
    }

    private val genreCacheFile by lazy {
        Injekt.get<Application>().cacheDir
            .resolve("source_$id")
            .also { it.mkdirs() }
            .resolve("genres.json")
    }
    private val genreLock = Any()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filters are ignored with text search"),
            StatusFilter(),
            TypeFilter(),
            SortFilter(),
        )

        if (genreCacheFile.exists()) {
            val fileContent = synchronized(genreLock) {
                genreCacheFile.readText()
            }
            val genres = fileContent.parseAs<List<Genre>>()
            filters.add(GenreFilter(genres))
        } else {
            filters.add(Filter.Separator())
            filters.add(Filter.Header("Press 'reset' to load genres"))
        }

        return FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string().also { cacheGenres(it) }
        val data = body.extractNextJsRsc<MangaList>()

        val mangas = data?.data.orEmpty().map { it.toSManga() }
        val hasNextPage = data?.hasNextPage() ?: false

        return MangasPage(mangas, hasNextPage)
    }

    private fun cacheGenres(body: String) {
        val genres = body.extractNextJsRsc<GenreList>()
            ?.genres
            ?.takeIf { it.isNotEmpty() }
            ?.toJsonString()
            ?: return

        synchronized(genreLock) {
            genreCacheFile.writeText(genres)
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/komik/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            url = document.selectFirst("meta[property=og:url]")!!.absUrl("content")
                .toHttpUrl().pathSegments[1]
            title = document.selectFirst("meta[property=og:title]")!!.attr("content")
                .removeSuffix(" - Komiku")
            thumbnail_url = document.selectFirst("img.object-cover")?.absUrl("src")
            author = document.selectFirst("span:contains(author:) + span")?.ownText()?.trim()
            genre = buildList {
                document.selectFirst("span:contains(type:) + span")
                    ?.ownText()?.trim()
                    ?.also { add(it) }
                document.selectFirst("span:contains(rilis:) + span")
                    ?.ownText()?.trim()
                    ?.also { add(it) }
                document.select(".bg-zinc-700").forEach {
                    add(it.ownText().trim())
                }
            }.joinToString()
            description = document.select("p.line-clamp-4").joinToString("\n") { it.ownText().trim() }
            status = when (document.selectFirst(".bg-gray-100.text-gray-800")?.ownText()?.trim()) {
                "Ongoing" -> SManga.ONGOING
                "Selesai" -> SManga.COMPLETED
                "Hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<ChaptersList>()

        return data?.chapters.orEmpty().map { it.toSChapter() }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), rscHeaders)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<Images>()

        return data?.images.orEmpty().mapIndexed { index, img ->
            Page(
                index,
                imageUrl = when {
                    img.startsWith("http") -> img
                    else -> CDN_URL + img.removePrefix("/")
                },
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
