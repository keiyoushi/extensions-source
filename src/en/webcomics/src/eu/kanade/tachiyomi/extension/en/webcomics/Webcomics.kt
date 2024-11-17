package eu.kanade.tachiyomi.extension.en.webcomics

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.PREF_KEY_RANDOM_UA
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Webcomics : ParsedHttpSource(), ConfigurableSource {

    override val name = "Webcomics"

    override val baseUrl = "https://webcomicsapp.com"

    private val apiUrl = "https://popeye.${baseUrl.substringAfterLast("/")}/api"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    // ========================== Popular =====================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genres/All/All/Popularity/$page", headers)

    override fun popularMangaSelector() = ".book-list .list-item a"

    override fun popularMangaNextPageSelector() = ".page-list li:not([style*=none]) a.next"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ========================== Latest =====================================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genres/All/All/Latest_Updated/$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ========================== Search =====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
            return GET(url.addPathSegment(query.toPathSegment()).build(), headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genre = filter.selected()
                    val url = "$baseUrl/genres/$genre/All/Popular/$page"
                    return GET(url, headers)
                }
                else -> {}
            }
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // ========================== Details ====================================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst(".card-info")!!
        title = infoElement.selectFirst("h5")!!.text()
        description = infoElement.selectFirst(".book-detail > p")?.text()
        genre = infoElement.select(".label-tag").joinToString { it.text() }
        thumbnail_url = infoElement.selectFirst("img")?.absUrl("src")
        document.selectFirst(".chapter-updateDetail")?.text()?.let {
            status = if (it.contains("IDK")) SManga.COMPLETED else SManga.ONGOING
        }
    }

    // ========================== Chapter ====================================

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/chapter/list?manga_id=$mangaId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ChapterWrapper>()
        val manga = dto.manga

        return dto.chapters.map { chapter ->
            SChapter.create().apply {
                name = if (chapter.is_pay) "🔒 ${chapter.name}" else chapter.name
                date_upload = chapter.update_time
                chapter_number = chapter.index.toFloat()

                val chapterUrl = "$baseUrl/view".toHttpUrl().newBuilder()
                    .addPathSegment(manga.name.replace(WHITE_SPACE_REGEX, "-"))
                    .addPathSegment(chapter.index.toString())
                    .addPathSegment("${manga.manga_id}-${chapter.name.toPathSegment()}")
                    .build()

                setUrlWithoutDomain(chapterUrl.toString())
            }
        }.sortedBy(SChapter::chapter_number).reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().trim()
        return chapter
    }

    // ========================== Pages ====================================

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script")
            .firstOrNull { PAGE_REGEX.containsMatchIn(it.data()) }
            ?: throw Exception("You may need to log in")

        return PAGE_REGEX.findAll(script.data()).mapIndexed { index, match ->
            Page(index, imageUrl = match.groups["img"]!!.value.unicode())
        }.toList()
    }

    override fun imageUrlParse(document: Document) = ""

    // ========================== Filters ==================================

    private class GenreFilter(val genres: Array<String>) : Filter.Select<String>("Genre", genres) {
        fun selected() = genres[state]
    }

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
    )

    private fun getGenreList() = arrayOf(
        "All",
        "Romance",
        "Fantasy",
        "Action",
        "Drama",
        "BL",
        "GL",
        "Comedy",
        "Horror",
        "Mistery",
    )

    // =============================== Utlis ====================================
    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun String.toPathSegment(): String {
        return this
            .replace(PUNCTUATION_REGEX, "")
            .replace(WHITE_SPACE_REGEX, "-")
    }

    fun String.unicode(): String {
        return UNICODE_REGEX.replace(this) { match ->
            val hex = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val value = hex.toInt(16)
            value.toChar().toString()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)

        // Force UA for desktop, as mobile versions return an empty page
        preferences.getString(PREF_KEY_RANDOM_UA, "off")?.let {
            if (it != "off") {
                return@let
            }
            preferences.edit()
                .putString(PREF_KEY_RANDOM_UA, "desktop")
                .apply()
        }
    }

    companion object {
        val PAGE_REGEX = """src:(\s+)?"(?<img>[^"]+)""".toRegex()
        val WHITE_SPACE_REGEX = """[\s]+""".toRegex()
        val PUNCTUATION_REGEX = "[\\p{Punct}]".toRegex()
        val UNICODE_REGEX = "\\\\u([0-9A-Fa-f]{4})|\\\\U([0-9A-Fa-f]{8})".toRegex()
    }
}
