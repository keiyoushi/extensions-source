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
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
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

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    private val dateRegex = """\d{4}\.\d{2}\.\d{2}""".toRegex()
    private val styleUrlRegex = """background-image\s*:\s*url\('([^']+)'\)""".toRegex()
    private val mediaCountRegex = """\d+P( \+ \d+V)?""".toRegex()

    private val mangaSelector = "div.list.photo-list > div.item.photo, div.list.amateur-list > div.item.amateur"
    private val nextPageSelector = "div.pager a.next"
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

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/photos/sort-hot/$page.html"))

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/photos/$page.html"))

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return parseMangaList(client.get(httpUrl))
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val basePageUrl = response.request.url.toString()
            .removeSuffix(".html")
        val document = response.asJsoup()

        return SMangaUpdate(
            manga = mangaDetailsParse(manga, document),
            chapters = chapterListParse(document, basePageUrl),
        )
    }

    private fun mangaDetailsParse(manga: SManga, document: Document): SManga = manga.apply {
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

    // ============================= Chapters ==============================

    private fun chapterListParse(doc: Document, basePageUrl: String): List<SChapter> {
        val infoCardElement = doc.selectFirst("div.info-card.photo-detail")!!
        val uploadAt = parseInfoByIcon(infoCardElement, "i.fa-calendar-days")
            ?.let { dateFormat.tryParseDate(it) }
            ?: parseUploadDateFromVersionInfo(doc)
            ?: 0L
        val maxPage = doc.select("div.pager:first-of-type a.pager-num").last()?.text()?.toIntOrNull() ?: 1

        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                setUrlWithoutDomain("$basePageUrl/$page.html")
                name = "Page $page"
                date_upload = uploadAt
            }
        }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
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
            return dateFormat.tryParseDate(date.value)
        }
        return null
    }
}
