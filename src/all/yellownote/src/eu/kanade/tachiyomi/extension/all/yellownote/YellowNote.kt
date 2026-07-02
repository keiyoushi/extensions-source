package eu.kanade.tachiyomi.extension.all.yellownote

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.yellownote.Preferences.baseUrl
import eu.kanade.tachiyomi.extension.all.yellownote.Preferences.language
import eu.kanade.tachiyomi.extension.all.yellownote.Preferences.preferenceMigration
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
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

@Source
abstract class YellowNote :
    HttpSource(),
    ConfigurableSource {

    override val id = 170542391855030753L

    override val lang = "all"

    override val name = "小黄书"

    override val baseUrl: String get() = preferences.baseUrl()

    override val supportsLatest = true

    private val preferences = getPreferences { preferenceMigration() }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val intl get() = Intl(
        language = preferences.language(),
        baseLanguage = LanguageUtils.baseLocale.language,
        availableLanguages = LanguageUtils.supportedLocaleTags.toSet(),
        classLoader = this::class.java.classLoader!!,
    )

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT)

    // yyyy.MM.dd
    private val dateRegex = """\d{4}\.\d{2}\.\d{2}""".toRegex()

    // <div role="img" class="img" style="background-image:url('https://img.xchina.io/photos/641aea8f589cb/0068_600x0.webp');"></div>
    private val styleUrlRegex = """background-image\s*:\s*url\('([^']+)'\)""".toRegex()

    // 100P + 2V
    private val mediaCountRegex = """\d+P( \+ \d+V)?""".toRegex()

    private val mangaSelector = "div.list.photo-list > div.item.photo, div.list.amateur-list > div.item.amateur"
    private val nextPageSelector = "div.pager:first-of-type > a.pager-next"
    private val imageSelector = "div.list.photo-items > div.item.photo-image, div.list.amateur-items > div.item.amateur-image"

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/photos/sort-hot/$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/photos/$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categorySelector = filters.firstInstance<CategorySelector>()
        val sortSelector = filters.firstInstance<SortSelector>()
        val uriPart = when {
            query.isBlank() -> categorySelector.toUriPart()
            else -> "photos/keyword-$query"
        }

        val httpUrl = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(uriPart)
            val sort = sortSelector.toUriPart()
            if (sort.isNotEmpty()) {
                addPathSegment(sort)
            }
            addPathSegment("$page.html")
        }.build()
        return GET(httpUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val infoCardElement = document.selectFirst("div.info-card.photo-detail")!!
            val name = parseInfoByIcon(infoCardElement, "i.fa-address-card")!!
            val mediaCount = parseInfoByIcon(infoCardElement, "i.fa-image")!!
            val no = parseInfoByIcon(infoCardElement, "i.fa-file")?.let { " $it" }.orEmpty()
            val categories =
                parseInfosByIcon(infoCardElement, "i.fa-video-camera")?.filter { it != "-" }
            val filters = parseInfosByIcon(infoCardElement, "i.fa-filter")
            val tags = parseInfosByIcon(infoCardElement, "i.fa-tags")

            title = "$name$no($mediaCount)"
            author = infoCardElement.selectFirst("div.item.floating")
                ?.text()
                ?: parseInfoByIcon(infoCardElement, "i.fa-circle-user")
            genre = listOfNotNull(categories, filters, tags)
                .flatten()
                .takeIf { it.isNotEmpty() }
                ?.joinToString()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val infoCardElement = doc.selectFirst("div.info-card.photo-detail")!!
        val uploadAt = parseInfoByIcon(infoCardElement, "i.fa-calendar-days")
            ?.let { dateFormat.tryParse(it) }
            ?: parseUploadDateFromVersionInfo(doc)
            ?: 0L
        val maxPage = doc.select("div.pager:first-of-type a.pager-num").last()?.text()?.toIntOrNull() ?: 1
        val basePageUrl = response.request.url.toString()
            .removeSuffix(".html")
        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                setUrlWithoutDomain("$basePageUrl/$page.html")
                name = "Page $page"
                date_upload = uploadAt
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(imageSelector).mapIndexedNotNull { i, imageElement ->
            val imgEl = imageElement.selectFirst("div.img") ?: return@mapIndexedNotNull null
            val url = parseUrlFormStyle(imgEl) ?: return@mapIndexedNotNull null
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        Filters.createSortSelector(intl),
        Filter.Separator(),
        Filter.Header(intl["filter.header.ignored-when-search"]),
        Filters.createCategorySelector(intl),
    )

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Preferences.buildPreferences(screen.context, intl)
            .forEach(screen::addPreference)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(mangaSelector).mapNotNull { element ->
            val mangaEl = element.selectFirst("a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(mangaEl.absUrl("href"))

                val formatMediaCount = element.select("div.tags > div")
                    .map { it.text() }
                    .firstOrNull { mediaCountRegex.matches(it) }
                    ?.let { "($it)" }
                    .orEmpty()
                title = "${mangaEl.attr("title")}$formatMediaCount"

                thumbnail_url = parseUrlFormStyle(mangaEl.selectFirst("div.img"))
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
        val hasNextPage = document.selectFirst(nextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseUrlFormStyle(element: Element?): String? = element
        ?.attr("style")
        ?.let { styleUrlRegex.find(it) }
        ?.groupValues
        ?.get(1)

    private fun parseInfosByIcon(infoCardElement: Element, iconClass: String): List<String>? = infoCardElement
        .selectFirst("div.item:has(.icon > $iconClass)")
        ?.selectFirst("div.text")
        ?.children()
        ?.map { it.text() }

    private fun parseInfoByIcon(infoCardElement: Element, iconClass: String): String? = infoCardElement
        .selectFirst("div.item:has(.icon > $iconClass)")
        ?.selectFirst("div.text")
        ?.text()

    private fun parseUploadDateFromVersionInfo(doc: Document): Long? {
        for (info in doc.select("div.tab-content > div.info-card div.text")) {
            val date = dateRegex.find(info.text()) ?: continue
            return dateFormat.tryParse(date.value)
        }
        return null
    }
}
