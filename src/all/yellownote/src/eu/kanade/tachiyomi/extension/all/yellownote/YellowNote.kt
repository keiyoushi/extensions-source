package eu.kanade.tachiyomi.extension.all.yellownote

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.yellownote.YellowNoteFilters.SortSelector
import eu.kanade.tachiyomi.extension.all.yellownote.YellowNotePreferences.baseUrl
import eu.kanade.tachiyomi.extension.all.yellownote.YellowNotePreferences.preferenceMigration
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class YellowNote(
    override val lang: String,
    private val subdomain: String? = null,
) : SimpleParsedHttpSource(), ConfigurableSource {

    override val baseUrl by lazy { preferences.baseUrl(subdomain) }

    override val name = "小黄书"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences = getPreferences { preferenceMigration() }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val intl = Intl(
        language = lang,
        baseLanguage = YellowNoteSourceFactory.BASE_LANGUAGE,
        availableLanguages = YellowNoteSourceFactory.SUPPORT_LANGUAGES,
        classLoader = this::class.java.classLoader!!,
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val styleUrlRegex = """url\(['"]?([^'"]+)['"]?\)""".toRegex()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        YellowNotePreferences.buildPreferences(screen.context, intl)
            .forEach(screen::addPreference)
    }

    override fun simpleMangaSelector() = "div.article > div.list > div.item:not([class*=item exoclick_300x500])"

    override fun simpleMangaFromElement(element: Element): SManga {
        if (element.hasClass("amateur")) {
            return simpleMangaFromElementByAmateur(element)
        }

        return SManga.create().apply {
            val imgEl = element.selectFirst("img")!!
            val titleAppend = element.selectFirst("div.tag > div")?.text()?.let { "($it)" }.orEmpty()
            title = "${imgEl.attr("alt")}$titleAppend"

            thumbnail_url = imgEl.absUrl("src")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        }
    }

    // /amateurs
    private fun simpleMangaFromElementByAmateur(element: Element) = SManga.create().apply {
        val titleAppend = element.selectFirst("div.tag > div")?.text()?.let { "($it)" }.orEmpty()
        title = "${element.selectFirst("div:nth-child(3)")!!.text()}$titleAppend"

        thumbnail_url = element.selectFirst(".img")?.attr("style")
            ?.let { styleUrlRegex.find(it) }
            ?.groupValues
            ?.get(1)

        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.absUrl("href"))
    }

    override fun simpleNextPageSelector() = "div.pager:first-of-type a[current] + a[href]"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/photos/sort-hot/$page.html", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/photos/$page.html", headers)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val tabEl = document.selectFirst("div#tab_1")!!
        val titleAppend = tabEl.selectFirst("i.fa.fa-picture-o")?.parentText()?.let { "($it)" }.orEmpty()
        title = "${tabEl.selectFirst("i.fa.fa-address-card-o")!!.parentText()!!}$titleAppend"

        author = tabEl.select("div.models > a").joinToString { it.text() }
        genre = tabEl.select("div.contentTag").joinToString { it.text() }
        status = SManga.COMPLETED
        description = tabEl.selectFirst("i.fa.fa-calendar")?.text()
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val dateUploadStr = doc.selectFirst("i.fa.fa-calendar")?.text()
        val dateUpload = dateFormat.tryParse(dateUploadStr)
        val maxPage = doc.select("div.pager:first-of-type a:not([class])").last()?.text()?.toInt() ?: 1
        val basePageUrl = response.request.url.toString()
            .removeSuffix(".html")
        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                chapter_number = 0F
                setUrlWithoutDomain("$basePageUrl/$page.html")
                name = "Page $page"
                date_upload = dateUpload
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.article.mask .photos img.cr_only")
            .mapIndexed { i, imgEl -> Page(i, imageUrl = imgEl!!.absUrl("src")) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categorySelector = filters.firstInstance<YellowNoteFilters.CategorySelector>()
        val sortSelector = filters.firstInstance<SortSelector>()
        val uriPart = when {
            query.isBlank() -> categorySelector.toUriPart()
            else -> "photos/keyword-$query"
        }

        val httpUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(uriPart)
            .addPathSegment(sortSelector.toUriPart())
            .addPathSegments("$page.html")
            .build()
        return GET(httpUrl, headers)
    }

    override fun getFilterList() = FilterList(
        YellowNoteFilters.createSortSelector(intl),
        Filter.Separator(),
        Filter.Header(intl["filter.header.ignored-when-search"]),
        YellowNoteFilters.createCategorySelector(intl),
    )
}
