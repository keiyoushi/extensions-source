package eu.kanade.tachiyomi.extension.en.rizzcomic

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class RizzComic : HttpSource(), ConfigurableSource {

    override val name = "Rizz Comic"

    override val lang = "en"

    override val baseUrl = "https://rizzcomic.com"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                preferences.getPrefUAType(),
                preferences.getPrefCustomUA(),
            )
            .rateLimit(1, 3)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

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
                val document = client.newCall(GET("$baseUrl/series", headers))
                    .execute().use { it.asJsoup() }

                urlPrefix = document.selectFirst(".listupd a")
                    ?.attr("href")
                    ?.substringAfter("/series/")
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
            .map { mangaDetailsParse(it, manga) }
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

    private fun mangaDetailsParse(response: Response, manga: SManga) = manga.apply {
        val document = response.use { it.asJsoup() }

        title = document.selectFirst("h1.entry-title")?.text().orEmpty()
        artist = document.selectFirst(".tsinfo .imptdt:contains(artist) i")?.ownText()
        author = listOfNotNull(
            document.selectFirst(".tsinfo .imptdt:contains(author) i")?.ownText(),
            document.selectFirst(".tsinfo .imptdt:contains(serialization) i")?.ownText(),
        ).joinToString()
        genre = buildList {
            add(
                document.selectFirst(".tsinfo .imptdt:contains(type) a")
                    ?.ownText()
                    ?.capitalize(),
            )
            document.select(".mgen a").eachText().onEach { add(it) }
        }.filterNotNull().joinToString()
        status = document.selectFirst(".tsinfo .imptdt:contains(status) i")?.text().parseStatus()
        thumbnail_url = document.selectFirst(".infomanga > div[itemprop=image] img, .thumb img")?.absUrl("src")
    }

    private fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { contains(it, ignoreCase = true) } -> SManga.ONGOING
        contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        contains("completed", ignoreCase = true) -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("#")
        val slug = manga.url.substringBefore("#")

        return GET("$baseUrl/index/search_chapters/$id#$slug", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<Chapter>>()
        val slug = response.request.url.fragment!!

        return result.map {
            SChapter.create().apply {
                url = "$slug-chapter-${it.name}"
                name = "Chapter ${it.name}"
                date_upload = runCatching {
                    dateFormat.parse(it.time!!)!!.time
                }.getOrDefault(0L)
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/chapter/${getUrlPrefix()}-${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.use { it.asJsoup() }
        val chapterUrl = response.request.url.toString()

        return document.select("div#readerarea img")
            .mapIndexed { i, img ->
                Page(i, chapterUrl, img.absUrl("src"))
            }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException()
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private inline fun <reified T> Response.parseAs(): T =
        use { it.body.string() }.let(json::decodeFromString)

    companion object {
        private fun String.capitalize() = replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase(Locale.ROOT)
            } else {
                it.toString()
            }
        }

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }
    }
}
