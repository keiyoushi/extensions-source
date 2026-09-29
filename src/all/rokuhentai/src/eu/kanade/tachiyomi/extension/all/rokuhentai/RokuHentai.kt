package eu.kanade.tachiyomi.extension.all.rokuhentai

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class RokuHentai :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    // Customize

    private val pref by getPreferencesLazy()
    private val offset = ConcurrentHashMap<String, String>()
    private val SManga.id get() = url.substringAfterLast('/').substringBefore('#')

    companion object {
        val DATE_FORMAT = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d, yyyy, h:mm a")
            .toFormatter(Locale.US)
        val IMG_REGEX = Regex("""background-image: url\("(.+?)"\);""")
        const val THUMBNAIL_PREF = "THUMBNAIL"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            SwitchPreferenceCompat(screen.context).apply {
                key = THUMBNAIL_PREF
                title = "Use thumbnails instead of the original images"
                summary = "After enabling, each page of the manga will display smaller and blurrier thumbnail."
                setDefaultValue(false)
            },
        )
    }

    private fun parseManga(e: Element) = SManga.create().apply {
        val info = e.selectFirst(".mdc-typography--caption:last-child")!!.text().split(" images ")
        setUrlWithoutDomain(e.absUrl("href").substringBeforeLast('/') + "#${info[0]},${DATE_FORMAT.tryParseDateTime(info[1], ZoneOffset.UTC)}")
        title = e.selectFirst(".site-manga-card__title--primary")!!.text()
        thumbnail_url = IMG_REGEX.matchEntire(e.selectFirst(".mdc-card__media")!!.attr("style"))?.groups[1]?.value
    }

    // Popular Page

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/_search?p=${offset["popular"]}"
        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val type = response.header("Content-Type")!!
        val mangas = if (type.contains("text/html")) {
            response.asJsoup().select(".mdc-card > .site-popunder-ad-slot").map(::parseManga)
        } else {
            response.parseAs<SearchResult>().mangaCards.map {
                parseManga(Jsoup.parse(it, baseUrl).selectFirst(".site-popunder-ad-slot")!!)
            }
        }
        mangas.lastOrNull()?.let { last ->
            val key = response.request.url.queryParameter("q")?.let { "search:$it" } ?: "popular"
            offset[key] = last.id
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // Latest Page

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // Search Page

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Search How-To:"),
        Filter.Header("• Search for a phrase in manga titles: this is a title."),
        Filter.Header("• Search for manga titles containing all phrases (double quotes are required to separate phrases): \"this foo\" \"that bar\"."),
        Filter.Header("• Exclude manga titles containing a phrase with \"-\" (double quotes are required): -\"this is excluded\"."),
        Filter.Header("• Filter mangas by tag: group:foo."),
        Filter.Header("• You must double-quote a tag if it contains spaces: parody:\"foo bar\"."),
        Filter.Header("• Filter mangas by date: after:2000-01-01 before:2000-02."),
        Filter.Header("• Negate a filter with \"-\": -language:foo."),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("q", query)
        if (page > 1) {
            url.addPathSegment("_search").addQueryParameter("p", offset["search:$query"])
        }
        return parseMangasPage(client.get(url.build()))
    }

    // Manga Detail

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (fetchDetails) {
            val doc = client.get(getMangaUrl(manga)).asJsoup()
            val img = doc.selectFirst(".site-manga-info .mdc-card__media")
            val titles = doc.select(".site-manga-info__info h6")
            manga.apply {
                thumbnail_url = IMG_REGEX.matchEntire(img!!.attr("style"))?.groups[1]?.value
                description = titles.getOrNull(1)?.text()
                genre = doc.select(".mdc-chip").joinToString {
                    val text = it.text().split(": ")
                    if (text[0] == "artist") author = text[1]
                    if (text[1].contains(' ')) "${text[0]}: \"${text[1]}\"" else it.text()
                }
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }

        val chapter = SChapter.create().apply {
            url = manga.id
            name = manga.title
            date_upload = manga.url.substringAfter(',').toLong()
            chapter_number = 0F
            scanlator = "${manga.url.substringAfter('#').substringBefore(',')}P"
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    // Chapter

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}/0#top-to-bottom"

    // Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val size = chapter.scanlator!!.substringBefore('P').toInt()
        val path = if (pref.getBoolean(THUMBNAIL_PREF, false)) "page-thumbnails" else "pages"
        return List(size) { i -> Page(i, imageUrl = "$baseUrl/_images/$path/${chapter.url}/$i.jpg") }
    }
}
