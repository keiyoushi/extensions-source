package eu.kanade.tachiyomi.extension.en.rizzcomic

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.Locale

@Source
abstract class RizzComic : MangaThemesiaAlt() {
    override val mangaUrlDirectory = "/series"
    override val datePattern = "dd MMM yyyy"
    override val pageSelector = "div#readerarea > img"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            val isApiRequest = request.header("X-API-Request") != null
            val headers = request.headers.newBuilder().apply {
                if (!isApiRequest) removeAll("X-Requested-With")
                removeAll("X-API-Request")
            }.build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }

        rateLimit(1)
    }

    // For WebView
    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("X-Requested-With", randomString((1..20).random()))

    private val apiHeaders get() = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .set("X-API-Request", "1")
        .build()

    override val slugRegex = Regex("""^(r\d+-)""")

    // don't allow disabling random part setting
    override fun setupPreferenceScreen(screen: PreferenceScreen) = Unit

    override val listUrl = mangaUrlDirectory
    override val listSelector = "div.bsx a"

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.POPULAR)
    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val res = if (query.isNotEmpty()) {
            val form = FormBody.Builder()
                .add("search_value", query.trim())
                .build()

            client.post("$baseUrl/Index/live_search", apiHeaders, form)
        } else {
            val form = FormBody.Builder().apply {
                filters.filterIsInstance<FormBodyFilter>().forEach {
                    it.addFormParameter(this)
                }
            }.build()

            client.post("$baseUrl/Index/filter_series", apiHeaders, form)
        }

        return parseSearchManga(res)
    }

    override val supportsFilterFetching = false

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filters don't work with text search"),
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    @Serializable
    class Comic(
        val title: String,
        val id: String,
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
            .replace("-", " ")
            .replace("'s", "s")
            .replace("'", "")
            .replace(slugRegex, "-")
            .replace("-ll-", "ll-")
            .trim('-')

        val genreIds get() = genres?.split(",")?.map(String::trim)

        companion object {
            private val slugRegex = Regex("""[^a-z0-9]+""")
        }
    }

    private fun parseSearchManga(response: Response): MangasPage {
        val result = response.parseAs<List<Comic>>()

        val entries = result.map { comic ->
            SManga.create().apply {
                url = "$mangaUrlDirectory/${comic.slug}/#${comic.id}"
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
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = super.fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters).apply {
        this.manga.description = manga.description
    }

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
