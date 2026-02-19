package eu.kanade.tachiyomi.extension.en.webcomics

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
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Webcomics :
    HttpSource(),
    ConfigurableSource {

    override val name = "Webcomics"

    override val baseUrl = "https://webcomicsapp.com"

    private val apiUrl = "https://popeye.${baseUrl.substringAfterLast("/")}/api"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences = getPreferences()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.isSearchRequest()) {
                val ua = getDesktopUA()

                val newHeaders = request.headers.newBuilder()
                    .set("User-Agent", ua.desktop.random())
                    .build()

                val newRequest = request.newBuilder()
                    .headers(newHeaders)
                    .build()
                return@addInterceptor chain.proceed(newRequest)
            }
            chain.proceed(request)
        }
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .build()

    private fun Request.isSearchRequest(): Boolean = url.pathSegments.contains("search") || url.pathSegments.count { segment -> segment == "All" } == 1

    private var userAgentList: UserAgentList? = null

    private fun getDesktopUA(): UserAgentList = userAgentList ?: network.cloudflareClient.newCall(GET(UA_DB_URL))
        .execute().parseAs<UserAgentList>().also {
            userAgentList = it
        }

    // ========================== Popular =====================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genres/All/All/Popularity/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#All a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h5")!!.text()
                thumbnail_url = element.selectFirst("img[src]")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }

        val hasNextPage = document.selectFirst("script:containsData(__NUXT__)")?.data()
            ?.substringAfter("page:")
            ?.substringBefore(",")
            ?.let { it.toIntOrNull() != null } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ========================== Latest =====================================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genres/All/All/Latest_Updated/$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

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

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".list-item a").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".info-title")!!.text()
                thumbnail_url = element.selectFirst("img[src]")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }

        return MangasPage(mangas, false)
    }

    // ========================== Details ====================================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val infoElement = document.selectFirst(".card-info")!!

        return SManga.create().apply {
            title = infoElement.selectFirst("h5")!!.text()
            description = infoElement.selectFirst(".book-detail > p")?.text()
            genre = infoElement.select(".label-tag").joinToString { it.text() }
            thumbnail_url = infoElement.selectFirst("img")?.absUrl("src")
            document.selectFirst(".chapter-updateDetail")?.text()?.let {
                status = if (it.contains("IDK")) SManga.COMPLETED else SManga.ONGOING
            }
        }
    }

    // ========================== Chapter ====================================

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

    // ========================== Pages ====================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val script = document.select("script:containsData(__NUXT__)")
            .firstOrNull { PAGE_REGEX.containsMatchIn(it.data()) }
            ?: throw Exception("You may need to log in")

        return PAGE_REGEX.findAll(script.data()).mapIndexed { index, match ->
            Page(index, imageUrl = match.groupValues.last().unicode())
        }.toList()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

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

    private fun String.toPathSegment(): String = this
        .replace(PUNCTUATION_REGEX, "")
        .replace(WHITE_SPACE_REGEX, "-")

    fun String.unicode(): String = UNICODE_REGEX.replace(this) { match ->
        val hex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val value = hex.toInt(16)
        value.toChar().toString()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)

        preferences.getString(PREF_KEY_RANDOM_UA, "off")?.let {
            if (it != "off") {
                return@let
            }
            preferences.edit()
                .putString(PREF_KEY_RANDOM_UA, "mobile")
                .apply()
        }
    }

    companion object {
        val PAGE_REGEX = """src:(?:\s+)?"([^"]+)""".toRegex()
        val WHITE_SPACE_REGEX = """[\s]+""".toRegex()
        val PUNCTUATION_REGEX = "[\\p{Punct}]".toRegex()
        val UNICODE_REGEX = "\\\\u([0-9A-Fa-f]{4})|\\\\U([0-9A-Fa-f]{8})".toRegex()

        private const val UA_DB_URL = "https://keiyoushi.github.io/user-agents/user-agents.json"
    }
}
