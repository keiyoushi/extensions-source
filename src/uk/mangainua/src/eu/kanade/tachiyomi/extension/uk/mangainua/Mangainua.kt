package eu.kanade.tachiyomi.extension.uk.mangainua

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.getValue

class Mangainua :
    HttpSource(),
    ConfigurableSource {
    // Info
    override val name = "MANGA/in/UA"
    override val baseUrl = "https://manga.in.ua"
    override val lang = "uk"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = makeSearchRequest("news_read;desc", page)

    override fun popularMangaParse(response: Response) = mangaParse(response)

    // ============================== Latest ======================================
    override fun latestUpdatesRequest(page: Int) = makeSearchRequest("date;desc", page)

    override fun latestUpdatesParse(response: Response) = mangaParse(response)

    // ============================== Search ======================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = makeSearchRequest(null, page, query, filters)

    override fun searchMangaParse(response: Response) = mangaParse(response, true)

    private fun makeSearchRequest(sortBy: String? = null, page: Int, query: String = "", filters: FilterList = FilterList()): Request {
        // Search by title
        if (query.isNotEmpty()) {
            if (query.length < 3) {
                throw Exception("Запит має містити щонайменше 3 символи / The query must contain at least 3 characters")
            }

            return POST(
                "$baseUrl/index.php?do=search",
                body = FormBody.Builder()
                    .add("do", "search")
                    .add("subaction", "search")
                    .add("full_search", "1")
                    .add("story", query)
                    .add("search_start", page.toString())
                    .add("result_from", (1 + 12 * (page - 1)).toString())
                    .build(),
                headers = headers,
            )
        }

        // Search by filters
        val url = "$baseUrl/filter".toHttpUrl().newBuilder().apply {
            val ignoredTagsSettings = ignoreTags()
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is TagFilter -> {
                        filter.included?.let { addPathSegment("cat=${it.joinToString(",")}") }
                        filter.excluded?.let {
                            val filter = when {
                                ignoredTagsSettings.isNotEmpty() -> (ignoredTagsSettings + it).distinct().joinToString(",")
                                else -> it.joinToString(",")
                            }
                            addPathSegment("!cat=$filter")
                        } ?: run {
                            if (ignoredTagsSettings.isNotEmpty()) {
                                addPathSegment("!cat=${ignoredTagsSettings.joinToString(",")}")
                            }
                        }
                    }
                    is StatusFilter -> filter.selected?.let { addPathSegment("b.tra=$it") }
                    is CategoriesFilter -> filter.selected?.let { addPathSegment("b.type=$it") }
                    is AgeFilter -> filter.selected?.let { addPathSegment("b.vik=$it") }
                    is SizeFilter -> filter.selected?.let { addPathSegment("c.lastchappr=$it") }
                    is YearsFilter -> filter.selected?.let { addPathSegment("c.yer=$it") }
                    is SortFilter -> {
                        addPathSegment("sort=${sortBy ?: filter.selected}")
                    }
                    else -> {}
                }
            }
            if (page > 1) addPathSegments("page/$page/")
        }.build()

        return GET(url, headers)
    }

    private fun mangaParse(response: Response, fromSearch: Boolean = false): MangasPage {
        val document = response.asJsoup()
        val ignoredTags = if (hideChaptersByTag && fromSearch) {
            val ignoredIds = ignoreTags()
            TagFilter.options.filter { it.second in ignoredIds }.map { it.first }.toSet()
        } else {
            emptySet()
        }
        val mangas = document.select("div#site-content article.item").mapNotNull { mangaFromElement(it, ignoredTags) }
        val hasNextPage = document.selectFirst("a:contains(Наступна)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element, ignoredTags: Set<String>): SManga? = SManga.create().apply {
        // If there is no img, it's (probably) deleted manga, but site still shows it in search results
        // Example: https://manga.in.ua/filter/sort=nameukr;asc/
        element.selectFirst("img") ?: return null

        // Hide manga by tags in Settings
        if (hideChaptersByTag && ignoredTags.isNotEmpty()) {
            val mangaTags = element.select("div.card__category a").eachText()
            if (mangaTags.any { it in ignoredTags }) {
                return null
            }
        }

        element.selectFirst("h3.card__title a")!!.run {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    // ============================== Manga Details ======================================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("span.UAName")!!.ownText()
        description = document.selectFirst("div.item__full-description p")?.wholeText()
        thumbnail_url = document.selectFirst("div.item__full-sidebar--poster img")?.imgAttr()
        status = when (document.getInfoElement("Статус перекладу:")?.text()) {
            "Триває" -> SManga.ONGOING
            "Заморожено" -> SManga.ON_HIATUS
            "Покинуто" -> SManga.CANCELLED
            "Закінчений" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = buildList {
            add(document.getInfoElement("Тип:")?.text())
            addAll(document.getInfoElement("Жанри:")?.select("a")?.eachText().orEmpty())
        }.joinToString()
    }

    private fun Document.getInfoElement(text: String): Element? = selectFirst("div.item__full-sideba--header:has(div:containsOwn($text)) span.item__full-sidebar--description")

    // ============================== Chapters ======================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val newResponse = getAjaxChapters(response)
        return newResponse.asJsoup().select("div.ltcitems").asReversed().mapNotNull { element ->
            val urlElement = element.selectFirst("a") ?: return@mapNotNull null // Skip if no URL element
            val chapterName = urlElement.ownText().trim()
            val chapterNumber = element.attr("manga-chappter")
            val volumeNumber = element.attr("manga-tom")

            // Skip creating SChapter if both chapterNumber and volumeNumber are empty
            if (chapterNumber.isEmpty() && volumeNumber.isEmpty()) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                setUrlWithoutDomain(urlElement.attr("abs:href"))
                scanlator = element.attr("translate").takeUnless(String::isBlank) ?: urlElement.text().substringAfter("від:").trim()
                date_upload = parseDate(element.child(0).ownText())
                chapter_number = chapterNumber.toFloatOrNull() ?: 0f
                name = when {
                    chapterName.contains("Альтернативний") -> "Том $volumeNumber. Розділ $chapterNumber"
                    else -> chapterName
                }
            }
        }
    }

    private fun getAjaxChapters(response: Response): Response {
        val document = response.asJsoup()
        val userHash = document.parseUserHash()
        val endpoint = "engine/ajax/controller.php?mod=load_chapters"
        val userHashQuery = document.parseUserHashQuery(endpoint)
        val newsId = document.selectFirst("div#linkstocomics")!!.attr("data-news_id")
        val newsCategory = document.selectFirst("div#linkstocomics")!!.attr("data-news_category")
        val thisLink = document.selectFirst("div#linkstocomics")!!.attr("data-this_link")

        val newBody = FormBody.Builder()
            .add("action", "show")
            .add("news_id", newsId)
            .add("news_category", newsCategory)
            .add("this_link", thisLink)
            .add(userHashQuery, userHash)
            .build()

        return client.newCall(POST("$baseUrl/$endpoint", ajaxHeaders(), newBody))
            .execute()
    }

    // ============================== Images ======================================

    override fun pageListParse(response: Response): List<Page> {
        val document = getAjaxImages(response)
        return document.asJsoup().select("li img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("data-src"))
        }
    }

    private fun getAjaxImages(response: Response): Response {
        val document = response.asJsoup()
        val userHash = document.parseUserHash()
        val endpoint = "engine/ajax/controller.php?mod=load_chapters_image"
        val userHashQuery = document.parseUserHashQuery(endpoint)
        val newsId = document.selectFirst("div#comics")!!.attr("data-news_id")
        val url = "$baseUrl/$endpoint&news_id=$newsId&action=show&$userHashQuery=$userHash"

        return client.newCall(GET(url, ajaxHeaders()))
            .execute()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Utilities ======================================

    private fun ajaxHeaders() = headersBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    private fun Document.parseUserHash(): String {
        val script = selectFirst("script:containsData($SITE_LOGIN_HASH = )")?.data().orEmpty()
        val hash = script.substringAfter("$SITE_LOGIN_HASH = '").substringBefore("'")
        return hash.ifEmpty { throw Exception("Couldn't find user hash") }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Фільтри не застосовуються під час пошуку за назвою"),
        CategoriesFilter(),
        StatusFilter(),
        TagFilter(),
        SortFilter("news_read;desc"),
        SizeFilter(),
        AgeFilter(),
        YearsFilter(),
        Filter.Separator(),
    )

    private val userHashQueryRegex = Regex("""(\w+)\s*:\s*$SITE_LOGIN_HASH""")

    private fun Document.parseUserHashQuery(endpoint: String): String {
        val script = selectFirst("script:containsData($endpoint)")?.data()
            ?: throw Exception("Couldn't find user hash query script!")

        return userHashQueryRegex.find(script)?.groupValues?.get(1)
            ?: throw Exception("Couldn't find user hash query!")
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun ignoreTags(): Set<String> = preferences.getStringSet(SITE_TAGS_PREF, emptySet<String>())!!
    private val hideChaptersByTag = preferences.getBoolean(SITE_TAGS_SEARCH, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SITE_TAGS_PREF
            title = SITE_TAGS_PREF_TITLE
            val tags = TagFilter.options
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = tags.filter { it.second in ignoreTags() }
                .joinToString { it.first }
                .ifEmpty { "Не вибрано" } + SITE_TAGS_PREF_SUM
            dialogTitle = "Виберіть категорії які потрібно сховати"
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { "Не вибрано" } + SITE_TAGS_PREF_SUM
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SITE_TAGS_SEARCH
            title = SITE_TAGS_SEARCH_TITLE
            summary = SITE_TAGS_SEARCH_SUM
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private val dateFormatSite = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        private fun parseDate(dateStr: String?): Long = dateFormatSite.tryParse(dateStr)
        private const val SITE_LOGIN_HASH = "site_login_hash"
        private const val SITE_TAGS_PREF = "site_hidden_tags"
        private const val SITE_TAGS_PREF_TITLE = "Приховані категорії"
        private const val SITE_TAGS_PREF_SUM = "\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'."
        private const val SITE_TAGS_SEARCH = "site_hidden_tags_search"
        private const val SITE_TAGS_SEARCH_TITLE = "Приховувати обрані категорії при пошуку за назвою"
        private const val SITE_TAGS_SEARCH_SUM = "\nⓘ При зміні цього параметра необхідно перезапустити програму з повною зупинкою."
    }
}
