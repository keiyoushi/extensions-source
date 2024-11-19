package eu.kanade.tachiyomi.extension.en.mehgazone

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri.encode
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.en.mehgazone.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.en.mehgazone.dto.PageListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser.unescapeEntities
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Mehgazone : ConfigurableSource, HttpSource() {

    override val name = "Mehgazone"

    override val baseUrl = "https://mehgazone.com"

    override val lang = "en"

    override val supportsLatest = false

    @Suppress("VIRTUAL_MEMBER_HIDDEN", "unused")
    val supportsRelatedMangas = false

    override val client: OkHttpClient = network.cloudflareClient

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }

    private val textToImageURL = "https://fakeimg.pl/1500x2126/ffffff/000000/?font=museo&font_size=42"

    private fun String.image() = textToImageURL + "&text=" + encode(this)

    private fun String.unescape() = unescapeEntities(this, false)

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun getMangaUrl(manga: SManga) = manga.url

    override fun popularMangaParse(response: Response) = MangasPage(
        response.asJsoup()
            .selectFirst("#main aside.primary-sidebar .sidebar-group")!!
            .select("h2")
            .filter { el -> el.text().contains("Latest", true) }
            .map {
                SManga.create().apply {
                    title = it.text().split('"')[1].unescape()
                    url = it.nextElementSibling()!!.nextElementSibling()!!.selectFirst("a")!!.attr("href").substringBefore("/feed")
                    thumbnail_url = it.nextElementSibling()!!.selectFirst("img")!!.attr("src")
                }
            },
        false,
    )

    override fun mangaDetailsRequest(manga: SManga) =
        GET(manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val html = response.asJsoup()
        val thumbnailRegex = Regex("""/[^/]+-(?<file>[0-9]+\.png)$""", RegexOption.IGNORE_CASE)

        title = html.head().selectFirst("title")!!.text().unescape()
        url = response.request.url.toString()
        author = "Patricia Barton"
        status = SManga.ONGOING
        thumbnail_url =
            html.getElementById("content")!!
                .select("img")
                .firstOrNull { it.attr("src").matches(thumbnailRegex) }
                ?.attr("src")
                ?.replace(thumbnailRegex, "/$1")
    }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(url: String, page: Int): Request =
        GET("$url/wp-json/wp/v2/posts?per_page=100&page=$page&_fields=id,title,date_gmt,excerpt", headers)

    private fun hasNextPage(headers: Headers, responseSize: Int, page: Int): Boolean {
        val pages = headers["X-Wp-Totalpages"]?.toInt()
            ?: return responseSize == 100
        return page < pages
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override fun chapterListParse(response: Response): List<SChapter> {
        val apiResponse: MutableList<ChapterListDto> =
            json.decodeFromString<List<ChapterListDto>>(response.body.string()).toMutableList()
        val mangaUrl = response.request.url.toString().substringBefore("/wp-json/")

        if (hasNextPage(response.headers, apiResponse.size, 1)) {
            var page = 1
            do {
                page++
                val tempResponse = client.newCall(chapterListRequest(mangaUrl, page)).execute()
                val headers = tempResponse.headers
                val tempApiResponse: List<ChapterListDto> =
                    json.decodeFromString(tempResponse.body.string())

                apiResponse.addAll(tempApiResponse)
                tempResponse.close()
            } while (hasNextPage(headers, tempApiResponse.size, page))
        }

        return apiResponse
            .filter { showPatreon || !it.excerpt.rendered.contains("Unlock with Patreon") }
            .distinctBy { it.id }
            .sortedBy { it.date }
            .mapIndexed { i, it ->
                SChapter.create().apply {
                    url = "$mangaUrl/?p=${it.id}"
                    name = it.title.rendered.unescape()
                    date_upload = it.date.time
                    chapter_number = i.toFloat()
                }
            }.reversed()
    }

    // Adapted from the xkcd source's wordWrap function
    private fun wordWrap(text: String) = buildString {
        var charCount = 0
        text.replace('\n', ' ').split(' ').forEach { w ->
            if (charCount > 25) {
                append("\n")
                charCount = 0
            }
            append(w).append(' ')
            charCount += w.length + 1
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(chapter.url.substringBefore("/?") + "/wp-json/wp/v2/posts?per_page=1&include=" + chapter.url.substringAfter("p="), headers)

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse: PageListDto = json.decodeFromString<List<PageListDto>>(response.body.string()).first()

        if (showPatreon && apiResponse.excerpt.rendered.contains("Unlock with Patreon")) {
            return pageListParsePatreon(apiResponse)
        }

        val content = Jsoup.parse(apiResponse.content.rendered, apiResponse.link)

        val images = content.select("img")
            .mapIndexed { i, it -> Page(i, "", it.attr("src")) }
            .toMutableList()

        val numImages = images.size

        if (apiResponse.excerpt.rendered.isNotBlank()) {
            images.add(
                Page(
                    numImages,
                    "",
                    wordWrap(Jsoup.parse(apiResponse.excerpt.rendered.unescape()).text()).image(),
                ),
            )
        }

        return images.toList()
    }

    private fun pageListParsePatreon(apiResponse: PageListDto): List<Page> {
        val response = client.newCall(GET(apiResponse.link, headers)).execute()

        val content = response.asJsoup().getElementById("content")!!

        val images = content.select("img")
            .filter { el -> !el.attr("alt").contains("early access") }
            .mapIndexed { i, it -> Page(i, "", it.attr("src")) }
            .toMutableList()

        val numImages = images.size

        val excerpt = content.children()
            .filter { el -> el.tagName() == "p" }
            .joinToString("\n") { it.text().trim() }
            .split("\\s+".toRegex())
            .filterIndexed { i, _ -> i < 55 }
            .joinToString(" ")

        if (excerpt.isNotBlank()) {
            images.add(Page(numImages, "", wordWrap(excerpt.unescape()).image()))
        }

        return images.toList()
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    companion object {
        private const val SHOW_PATREON_PREF_KEY = "SHOW_PATREON"
        private const val SHOW_PATREON_PREF_TITLE = "Show Patreon chapters"
        private const val SHOW_PATREON_PREF_SUMMARY = "If checked, shows chapters that require you to be logged in through Patreon"
        private const val SHOW_PATREON_PREF_DEFAULT_VALUE = false
    }

    private var showPatreon = preferences.getBoolean(SHOW_PATREON_PREF_KEY, SHOW_PATREON_PREF_DEFAULT_VALUE)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PATREON_PREF_KEY
            title = SHOW_PATREON_PREF_TITLE
            summary = SHOW_PATREON_PREF_SUMMARY
            setDefaultValue(SHOW_PATREON_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                showPatreon = newValue as Boolean
                true
            }
        }.also(screen::addPreference)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.just(MangasPage(emptyList(), false))

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
