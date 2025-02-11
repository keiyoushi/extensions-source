package eu.kanade.tachiyomi.extension.en.rizzcomic

import android.app.Application
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class RealmOasis : MangaThemesia(
    "Realm Oasis",
    "https://realmoasis.com",
    "en",
    mangaUrlDirectory = "/comics",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
) {

    override val client = super.client.newBuilder()
        .rateLimit(1, 3)
        .addInterceptor { chain ->
            val request = chain.request()
            val isApiRequest = request.header("X-API-Request") != null
            val headers = request.headers.newBuilder().apply {
                if (!isApiRequest) removeAll("X-Requested-With")
                removeAll("X-API-Request")
            }.build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("X-Requested-With", randomString((1..20).random())) // For WebView

    private val apiHeaders by lazy {
        headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("X-API-Request", "1")
            .build()
    }

    override val versionId = 3

    private val preferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    private val mangaPath by lazy {
        client.newCall(GET(baseUrl, headers))
            .execute().asJsoup()
            .selectFirst(".listupd a")!!
            .absUrl("href")
            .toHttpUrl()
            .pathSegments[0]
            .also {
                mangaPathCache = it
            }
    }

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val form = FormBody.Builder()
                .add("search_value", query.trim())
                .build()

            return POST("$baseUrl/Index/live_search", apiHeaders, form)
        }

        val form = FormBody.Builder().apply {
            filters.filterIsInstance<FormBodyFilter>().forEach {
                it.addFormParameter(this)
            }
        }.build()

        return POST("$baseUrl/Index/filter_series", apiHeaders, form)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Filters don't work with text search"),
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            GenreFilter(),
        )
    }

    @Serializable
    class Comic(
        val id: String,
        val title: String,
        @SerialName("image_url") val cover: String? = null,
        @SerialName("long_description") val synopsis: String? = null,
        val status: String? = null,
        val type: String? = null,
        val artist: String? = null,
        val author: String? = null,
        val serialization: String? = null,
        @SerialName("genre_id") val genres: String? = null,
    ) {
        val genreIds get() = genres?.split(",")?.map(String::trim)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<Comic>>()

        val entries = result.map { comic ->
            SManga.create().apply {
                url = comic.id
                title = comic.title
                description = comic.synopsis
                author = listOfNotNull(comic.author, comic.serialization).joinToString()
                artist = comic.artist
                status = comic.status.parseStatus()
                thumbnail_url = comic.cover?.let { "$baseUrl/assets/images/$it" }
                genre = buildList {
                    add(comic.type?.capitalize())
                    comic.genreIds?.onEach { gId ->
                        add(genres.firstOrNull { it.second == gId }?.first)
                    }
                }.filterNotNull().joinToString()
                initialized = true
            }
        }

        return MangasPage(entries, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaPath)
            .addPathSegment(
                UrlUtils.generateSeriesLink(manga.url.toInt()),
            ).build()

        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        return buildString {
            append(baseUrl)
            append("/")
            append(mangaPathCache)
            append("/")
            append(
                UrlUtils.generateSeriesLink(manga.url.toInt()),
            )
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { mangaDetailsParse(it).apply { description = manga.description } }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            val chapUrl = element.selectFirst("a")!!.absUrl("href")

            val (seriesId, chapterId) = UrlUtils.extractChapterIds(chapUrl)
                ?: throw Exception("unable find chapter id from url")

            url = "$seriesId/$chapterId"
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val (seriesId, chapterId) = chapter.url.split("/").take(2).map(String::toInt)

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaPath)
            .addPathSegment(
                UrlUtils.generateChapterLink(seriesId, chapterId),
            ).build()

        return GET(url, headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (seriesId, chapterId) = chapter.url.split("/").take(2).map(String::toInt)

        return buildString {
            append(baseUrl)
            append("/")
            append(mangaPathCache)
            append("/")
            append(
                UrlUtils.generateChapterLink(seriesId, chapterId),
            )
        }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", "$baseUrl/")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private inline fun <reified T> Response.parseAs(): T =
        use { it.body.string() }.let(json::decodeFromString)

    private fun String.capitalize() = replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.ROOT)
        } else {
            it.toString()
        }
    }

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    private var mangaPathCache: String = ""
        get() {
            if (field.isBlank()) {
                field = preferences.getString(mangaPathPrefCache, "comics")!!
            }

            return field
        }
        set(newVal) {
            preferences.edit().putString(mangaPathPrefCache, newVal).apply()
            field = newVal
        }
}

private const val mangaPathPrefCache = "manga_path"
