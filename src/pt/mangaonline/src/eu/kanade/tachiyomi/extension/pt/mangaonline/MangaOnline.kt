package eu.kanade.tachiyomi.extension.pt.mangaonline

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaOnline() : ParsedHttpSource(), ConfigurableSource {
    override val lang = "pt-BR"

    override val supportsLatest = true

    override val name = "Manga Online"

    override val baseUrl = "https://mangaonline.biz"

    private var genresSet: Set<Genre> = emptySet()

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                preferences.getPrefUAType(),
                preferences.getPrefCustomUA(),
            )
            .rateLimitHost(baseUrl.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
            .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("a")!!.ownText()
        date_upload = element.selectFirst("a span.date")?.ownText()!!.toDate()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun chapterListSelector() = "div.episodiotitle"

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3 a")!!.ownText()
            .replace("Capítulo\\s+([\\d.]+)".toRegex(), "")
            .trim()

        thumbnail_url = element.selectFirst("img")?.absUrl("src")

        val mangaUrl = element.selectFirst("h3 a")!!.absUrl("href")
            .replace("-capitulo-[\\d-]+".toRegex(), "")
            .replace("capitulo", "manga")

        setUrlWithoutDomain(mangaUrl)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val url = "$baseUrl/capitulo/page/$page".toHttpUrl().newBuilder()
            .build()

        return client
            .newCall(GET(url, headers))
            .asObservableSuccess()
            .map {
                val document = it.asJsoup()
                val mangas = document
                    .select(latestUpdatesSelector())
                    .map { latestUpdatesFromElement(it) }
                    .distinctBy { it.title }

                /*
                 NOTE: do not navigate to the next page because the manga will be duplicated.
                 This end point launches new chapters unbundled.
                 */
                MangasPage(mangas, false)
            }
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val containerInfo = document.selectFirst("div.content > div.sheader")
        title = containerInfo!!.selectFirst("h1")!!.ownText()
        thumbnail_url = containerInfo.selectFirst("img")?.absUrl("src")
        description = containerInfo.selectFirst("p:last-child")?.ownText()
        genre = containerInfo.select("div.sgeneros a")
            .map { it.ownText() }
            .filter { it.length > MIN_LENGTH_GENRER }
            .joinToString()
        setUrlWithoutDomain(document.location())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[loading=lazy]").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3 a")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("h3 a")!!.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/mais-vistos/".toHttpUrl()
            .newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "div.content .item"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = ".pagination > .current + a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genre = (filters.first() as GenreList).selected
        if (genre.isGlobal()) {
            val url = "$baseUrl/${genre.id}/page/$page".toHttpUrl().newBuilder()
                .build()
            return GET(url, headers)
        }

        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addPathSegment(query)
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/genero/${genre.id}/page/$page".toHttpUrl().newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch {
            fetchMangaGenre()
        }

        genresSet += Genre.Global

        return FilterList(
            GenreList(
                title = "Gêneros",
                genres = genresSet.toTypedArray(),
            ),
        )
    }

    private fun fetchMangaGenre() {
        val request = client
            .newCall(GET("$baseUrl/generos/"))
            .execute()

        val document = request.asJsoup()

        genresSet = document.select(".wp-content a").map {
            val id = it.absUrl("href")
                .split("/")
                .last { it.isNotEmpty() }
            Genre(it.ownText(), id)
        }.toSet()
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        val MIN_LENGTH_GENRER = 1
        val DATE_FORMATTER = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    }
}

data class Genre(val name: String, val id: String) {
    fun isGlobal(): Boolean = this == Global
    override fun toString() = name

    companion object {
        val Global = Genre("Todos", "manga")
    }
}

class GenreList(title: String, private val genres: Array<Genre>) : Filter.Select<Genre>(title, genres) {
    val selected
        get() = genres[state]
}
