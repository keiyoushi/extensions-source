package eu.kanade.tachiyomi.extension.en.rizzcomic

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
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class RizzComic : MangaThemesia(
    "Rizz Comic",
    "https://rizzcomic.com",
    "en",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
) {

    override val client = super.client.newBuilder()
        .rateLimit(1, 3)
        .build()

    private val apiHeaders by lazy {
        headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    private var urlPrefix: String? = null
    private var genreCache: List<Pair<String, String>> = emptyList()
    private var attempts = 0

    private fun updateCache() {
        if ((urlPrefix.isNullOrEmpty() || genreCache.isEmpty()) && attempts < 3) {
            runCatching {
                val document = client.newCall(GET("$baseUrl$mangaUrlDirectory", headers))
                    .execute().use { it.asJsoup() }

                urlPrefix = document.selectFirst(".listupd a")
                    ?.attr("href")
                    ?.substringAfter("$mangaUrlDirectory/")
                    ?.substringBefore("-")

                genreCache = document.selectFirst(".filter .genrez")
                    ?.select("li")
                    .orEmpty()
                    .map {
                        val name = it.select("label").text()
                        val id = it.select("input").attr("value")

                        Pair(name, id)
                    }
            }

            attempts++
        }
    }

    private fun getUrlPrefix(): String {
        if (urlPrefix.isNullOrEmpty()) {
            updateCache()
        }

        return urlPrefix ?: throw Exception("Unable to update dynamic urls")
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
        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("Filters don't work with text search"),
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
        )

        filters += if (genreCache.isEmpty()) {
            listOf(
                Filter.Separator(),
                Filter.Header("Press reset to attempt to load genres"),
            )
        } else {
            listOf(
                GenreFilter(genreCache),
            )
        }

        return FilterList(filters)
    }

    @Serializable
    class Comic(
        val id: Int,
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
        updateCache()

        val result = response.parseAs<List<Comic>>()

        val entries = result.map { comic ->
            SManga.create().apply {
                url = "${comic.slug}#${comic.id}"
                title = comic.title
                description = comic.synopsis
                author = listOfNotNull(comic.author, comic.serialization).joinToString()
                artist = comic.artist
                status = comic.status.parseStatus()
                thumbnail_url = comic.cover?.let { "$baseUrl/assets/images/$it" }
                genre = buildList {
                    add(comic.type?.capitalize())
                    comic.genreIds?.onEach { gId ->
                        add(genreCache.firstOrNull { it.second == gId }?.first)
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

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringBefore("#")
        val randomPart = getUrlPrefix()

        return GET("$baseUrl/series/$randomPart-$slug", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringBefore("#")

        val urlPart = urlPrefix?.let { "$it-" } ?: ""

        return "$baseUrl/series/$urlPart$slug"
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).map { chapter ->
            chapter.apply {
                url = url.removeSuffix("/")
                    .substringAfter("/")
                    .substringAfter("-")
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/chapter/${getUrlPrefix()}-${chapter.url}", headers)
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
}
