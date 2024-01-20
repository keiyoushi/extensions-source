package eu.kanade.tachiyomi.extension.ja.rawz

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.UnsupportedOperationException

class RawZ : HttpSource() {

    override val name = "RawZ"

    override val baseUrl = "https://stmanga.com"

    private val apiUrl = "https://api.rawz.org/api"

    override val lang = "ja"

    override val supportsLatest = true

    private val json by injectLazy<Json>()

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query.trim())
            filters.filterIsInstance<UriPartFilter>().forEach {
                it.addQueryParameter(this)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", LIMIT.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        fetchGenres()

        val result = response.parseAs<Data<List<Manga>>>()

        val entries = result.data.map { it.toSManga() }
        val hasNextPage = result.data.size == LIMIT

        return MangasPage(entries, hasNextPage)
    }

    private var genreCache: List<Pair<String, String>> = emptyList()
    private var genreFetchAttempts = 0
    private var genreFetchFailed = false

    private fun fetchGenres() {
        if ((genreCache.isEmpty() || genreFetchFailed) && genreFetchAttempts < 3) {
            val genres = runCatching {
                client.newCall(
                    GET("$apiUrl/taxonomy-browse?type=genres&limit=100&page=1", headers),
                )
                    .execute()
                    .parseAs<Data<Taxonomy>>()
                    .data.genres.map {
                        Pair(it.name, it.id.toString())
                    }
            }

            genreCache = genres.getOrNull().orEmpty()
            genreFetchFailed = genres.isFailure
            genreFetchAttempts++
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            ChapterNumFilter(),
        )

        filters += if (genreCache.isEmpty()) {
            listOf(
                Filter.Separator(),
                Filter.Header("Press Reset to attempt to display genre"),
            )
        } else {
            listOf(
                GenreFilter(genreCache),
            )
        }

        return FilterList(filters)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl/manga/${manga.url.substringAfter(".")}")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<Data<Manga>>().data.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url
            .substringAfter("/manga/")
            .substringBefore(".")

        val id = manga.url.substringAfterLast(".")

        return GET("$apiUrl/manga/$id/childs#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<Data<List<Chapter>>>()
        val mangaSlug = response.request.url.fragment!!

        return result.data.map {
            SChapter.create().apply {
                url = "/read/$mangaSlug.${it.id}/${it.slug}"
                name = it.name
                date_upload = runCatching {
                    dateFormat.parse(it.createdAt!!)!!.time
                }.getOrDefault(0L)
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url
            .substringBeforeLast("/")
            .substringAfterLast(".")

        return GET("$apiUrl/child-detail/$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<Data<Pages>>()

        return result.data.images.mapIndexed { idx, img ->
            Page(idx, "", img.url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(body.string())
    }

    companion object {
        private const val LIMIT = 30
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
        }
    }
}
