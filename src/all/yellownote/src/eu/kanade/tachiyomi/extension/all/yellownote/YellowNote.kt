package eu.kanade.tachiyomi.extension.all.yellownote

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class YellowNote :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    // img.xchina.io blocks requests that do not look like browser image loads
    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host == baseUrl.toHttpUrl().host) {
            chain.proceed(request)
        } else {
            chain.proceed(request.newBuilder().header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*").build())
        }
    }

    private val intl by lazy {
        Intl(
            language = lang,
            baseLanguage = "en",
            availableLanguages = setOf("en", "es", "ko", "zh-Hans", "zh-Hant"),
            classLoader = this::class.java.classLoader!!,
        )
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val dateRegex = """\d{4}-\d{2}-\d{2}""".toRegex()
    private val mediaCountRegex = """\d+P( \+ \d+V)?""".toRegex()
    private val idRegex = """[0-9a-f]+""".toRegex()

    private val mangaSelector = "a[href^=/photo/]:has(> span.ph), a[href^=/amateur/]:has(> span.ph)"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGE_QUALITY
            title = intl["config.image_quality.title"]
            summary = intl["config.image_quality.summary"]
            entries = arrayOf("原图(JPG)", "高清(WebP)")
            entryValues = arrayOf("original", "webp_hd")
            setDefaultValue("original")
        }.also(screen::addPreference)
    }

    // The site ranks photo sets only in the "Popular today" section of /photos
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(listUrl("photos", 1), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("main section:first-of-type").select(mangaSelector).map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(listUrl("photos/new", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("type", "photo")
                .apply { if (page > 1) addQueryParameter("page", page.toString()) }
                .build()
            return parseMangaList(url)
        }

        val category = filters.firstInstance<CategorySelector>().toUriPart()
        val sort = filters.firstInstance<SortSelector>().toUriPart()
        return parseMangaList(listUrl(category, page, sort))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val type = url.pathSegments.getOrNull(0)?.takeIf { it == "photo" || it == "amateur" } ?: return null
        val id = url.pathSegments.getOrNull(1)?.takeIf(idRegex::matches) ?: return null

        val manga = SManga.create().apply { this.url = "/$type/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga), Fetch.DOCUMENT)
        val path = response.request.url.encodedPath
        val document = response.asJsoup()

        return SMangaUpdate(
            manga = mangaDetailsParse(manga, document),
            chapters = chapterListParse(document, path),
        )
    }

    private fun mangaDetailsParse(manga: SManga, document: Document): SManga = manga.apply {
        val header = document.selectFirst("main header")!!

        title = header.selectFirst("h1")!!.text()
        author = header.select("a[href^=/model/], a[href^=/user-amateurs/]")
            .joinToString { it.text() }
            .ifEmpty { null }
        genre = header.select("a[href^=/photo-series/]:not([href*=?]), a[href*=?filter=], a[href^=/photos/tag/]")
            .map { it.text().removePrefix("#") }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .info(header.select("span").map { it.ownText() }.firstOrNull(mediaCountRegex::matches))
            .build()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun chapterListParse(document: Document, path: String): List<SChapter> {
        val header = document.selectFirst("main header")!!
        // Photo sets list their versions (first one is the original upload); amateur sets only have a date in the header
        val uploadAt = (header.selectFirst("details li") ?: header).text()
            .let(dateRegex::find)
            ?.let { dateFormat.tryParseDate(it.value) }
            ?: 0L
        val pageCount = document.selectFirst("form[data-page-jump]")?.attr("data-total")?.toIntOrNull() ?: 1

        return (pageCount downTo 1).map { page ->
            SChapter.create().apply {
                url = if (page == 1) path else "$path/$page"
                name = "Page $page"
                date_upload = uploadAt
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // The site can redirect a later page to the set's first page, with ?go= naming the page asked for
        val landing = document.location().toHttpUrl()
        landing.queryParameter("go")?.let { go ->
            document = client.get(landing.resolve(go)!!, Fetch.DOCUMENT.from(landing)).asJsoup()
            if (document.location().toHttpUrl().queryParameter("go") != null) {
                throw Exception("Page not available, open it in WebView")
            }
        }
        val quality = preferences.getString(PREF_IMAGE_QUALITY, "original") ?: "original"

        return document.select("div[data-cols-grid] > a > img").mapIndexed { i, image ->
            val url = image.absUrl("src")
            // Original JPG behind the 600px WebP preview
            val finalUrl = if (quality == "original" && url.contains("_600x0.webp")) {
                url.replace("_600x0.webp", ".jpg")
            } else {
                url
            }
            Page(i, imageUrl = finalUrl)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filters.createSortSelector(intl),
        Filter.Separator(),
        Filter.Header(intl["filter.header.ignored-when-search"]),
        Filters.createCategorySelector(intl),
    )

    private fun listUrl(path: String, page: Int, sort: String = ""): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .apply { if (page > 1) addPathSegment(page.toString()) }
        .apply { if (sort.isNotEmpty()) addQueryParameter("sort", sort) }
        .build()

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("main").select(mangaSelector).map(::mangaFromElement)
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("> span.ph + span")!!.text()
        thumbnail_url = element.selectFirst("> span.ph > img")?.absUrl("src")
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    companion object {
        private const val PREF_IMAGE_QUALITY = "XChina::IMAGE_QUALITY"
    }
}
