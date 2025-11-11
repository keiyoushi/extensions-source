package eu.kanade.tachiyomi.extension.pt.mangaonline

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaOnline : ParsedHttpSource(), ConfigurableSource {
    override val lang = "pt-BR"

    override val supportsLatest = true

    override val name = "Manga Online"

    override val baseUrl = "https://mangaonline.biz"

    private var genresSet: Set<Genre> = emptySet()

    private val preferences: SharedPreferences = getPreferences()

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

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/capitulo/page/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangesPage = super.latestUpdatesParse(response)

        return MangasPage(
            mangesPage.mangas.distinctBy { it.title },
            mangesPage.hasNextPage,
        )
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val containerInfo = document.selectFirst("div.content > div.sheader")
        title = containerInfo!!.selectFirst("h1")!!.ownText()
        thumbnail_url = containerInfo.selectFirst("img")?.absUrl("src")
        description = containerInfo.selectFirst("p:last-child")?.ownText()
        genre = containerInfo.select("div.sgeneros a")
            .map { it.ownText() }
            .filter { it.length > 1 }
            .joinToString()
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

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/mais-vistos/", headers)

    override fun popularMangaSelector() = "div.content .item"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = ".pagination > .current + a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addPathSegment(query)
                .build()
            return GET(url, headers)
        }

        val path = when (val genre = (filters.first() as GenreList).selected) {
            Genre.GLOBAL -> "$baseUrl/${genre.id}"
            else -> "$baseUrl/genero/${genre.id}"
        }

        return GET("$path/page/$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch {
            fetchMangaGenre()
        }

        genresSet += Genre.GLOBAL

        return FilterList(
            GenreList(
                title = "Gêneros",
                genres = genresSet.toTypedArray(),
            ),
        )
    }

    private fun fetchMangaGenre() {
        try {
            val request = client
                .newCall(GET("$baseUrl/generos/", headers))
                .execute()

            val document = request.asJsoup()

            genresSet = document.select(".wp-content a").map { element ->
                val id = element.absUrl("href")
                    .split("/")
                    .last { it.isNotEmpty() }
                Genre(element.ownText(), id)
            }.toSet()
        } catch (e: Exception) {
            Log.e("MangaOnline", e.stackTraceToString())
        }
    }

    private fun String.toDate() =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
}

data class Genre(val name: String, val id: String) {
    override fun toString() = name

    companion object {
        val GLOBAL = Genre("Todos", "manga")
    }
}

class GenreList(title: String, private val genres: Array<Genre>) : Filter.Select<Genre>(title, genres) {
    val selected get() = genres[state]
}
