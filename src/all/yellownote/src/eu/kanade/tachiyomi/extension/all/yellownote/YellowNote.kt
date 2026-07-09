package eu.kanade.tachiyomi.extension.all.yellownote

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.getPreferencesLazy
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

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val intl by lazy {
        Intl(
            language = lang,
            baseLanguage = "en",
            availableLanguages = setOf("en", "es", "ko", "zh-Hans", "zh-Hant"),
            classLoader = this::class.java.classLoader!!,
        )
    }

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT)

    private val dateRegex = """\d{4}\.\d{2}\.\d{2}""".toRegex()
    private val styleUrlRegex = """background-image\s*:\s*url\('([^']+)'\)""".toRegex()
    private val mediaCountRegex = """\d+P( \+ \d+V)?""".toRegex()

    private val mangaSelector = "div.list.photo-list > div.item.photo, div.list.amateur-list > div.item.amateur"
    private val nextPageSelector = "div.pager:first-of-type > a.pager-next"
    private val imageSelector = "div.list.photo-items > div.item.photo-image, div.list.amateur-items > div.item.amateur-image"

    // ============================== Preferences ==========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "XChina::IMAGE_QUALITY"
            title = intl["config.image_quality.title"]
            summary = intl["config.image_quality.summary"]
            entries = arrayOf("原图(JPG)", "高清(WebP)")
            entryValues = arrayOf("original", "webp_hd")
            setDefaultValue("original")
        }.also(screen::addPreference)
    }

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

            val sortPart = sortSelector.toUriPart()
            if (sortPart.isNotBlank()) {
                addPathSegment(sortPart)
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
            val infoCardElement = document.selectFirst("div.info-card.photo-detail")
                ?: throw Exception("Could not find info card")

            val name = parseInfoByIcon(infoCardElement, "i.fa-address-card")
                ?: throw Exception("Could not find name")

            val mediaCount = parseInfoByIcon(infoCardElement, "i.fa-image")
                ?: throw Exception("Could not find media count")

            val no = parseInfoByIcon(infoCardElement, "i.fa-file")?.let { " $it" }.orEmpty()
            val categories = parseInfosByIcon(infoCardElement, "i.fa-video-camera")?.filter { it != "-" }
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
        val quality = preferences.getString("XChina::IMAGE_QUALITY", "original") ?: "original"

        return document.select(imageSelector)
            .mapIndexedNotNull { i, imageElement ->
                val url = parseUrlFormStyle(imageElement.selectFirst("div.img")) ?: return@mapIndexedNotNull null

                // PR #15991: Replace WebP with JPG for original quality
                val finalUrl = if (quality == "original" && url.contains("_600x0.webp")) {
                    url.replace("_600x0.webp", ".jpg")
                } else {
                    url
                }

                Page(i, imageUrl = finalUrl)
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

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(mangaSelector).mapNotNull { element ->
            val mangaEl = element.selectFirst("a") ?: return@mapNotNull null
            val mangaUrl = mangaEl.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mangaTitle = mangaEl.attr("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaUrl)

                val formatMediaCount = element.select("div.tags > div")
                    .map { it.text() }
                    .firstOrNull { mediaCountRegex.matches(it) }
                    ?.let { "($it)" }
                    .orEmpty()
                title = "$mangaTitle$formatMediaCount"

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
