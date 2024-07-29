package eu.kanade.tachiyomi.extension.en.rizzcomic

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class RizzComic : MangaThemesiaAlt(
    "Rizz Comic",
    "https://rizzfables.com",
    "en",
    mangaUrlDirectory = "/series",
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

    override val versionId = 2

    override val slugRegex = Regex("""^(r\d+-)""")

    // don't allow disabling random part setting
    override fun setupPreferenceScreen(screen: PreferenceScreen) = Unit

    override val listUrl = mangaUrlDirectory
    override val listSelector = "div.bsx a"

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
        val slug get() = title.trim().lowercase()
            .replace(slugRegex, "-")
            .replace("-s-", "s-")
            .replace("-ll-", "ll-")

        val genreIds get() = genres?.split(",")?.map(String::trim)

        companion object {
            private val slugRegex = Regex("""[^a-z0-9]+""")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<Comic>>()

        val entries = result.map { comic ->
            SManga.create().apply {
                url = "$mangaUrlDirectory/${comic.slug}/"
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { mangaDetailsParse(it).apply { description = manga.description } }
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
}
