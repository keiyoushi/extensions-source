package eu.kanade.tachiyomi.extension.uk.mangarama

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class Mangarama :
    Madara(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val chapterMode = ChapterMode.MangaAjax

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override val altNameSelector = ".post-content_item:contains(Альтернативна) .summary-content"

    override val mangaDetailsSelectorStatus = "div.summary-content, div.summary-heading:contains(Статус) + div"

    override val orderByFilterOptions = listOf(
        intl["order_by_filter_trending"] to "popular",
        intl["order_by_filter_latest"] to "updated",
        intl["order_by_filter_new"] to "new",
        intl["order_by_filter_az"] to "title_asc",
        "За назвою Я-А" to "title_desc",
        "За роком: новіші" to "year_desc",
        "За роком: старіші" to "year_asc",
    )

    // ============================ Popular =============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest("popular", page)

    // ============================ Latest =============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest("updated", page)

    // ============================ Search =============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest("popular", page, query, filters)

    private suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addPathSegment("")

            filters?.forEach { filter ->
                when (filter) {
                    is OrderBy -> filter.selected?.let { addQueryParameter("mcf_sort", it) }
                    is TranslatorsFilter -> filter.selected?.let { addQueryParameter("translator", it) }
                    is AgeLimit -> filter.selected?.let { addQueryParameter("mcf_age", it) }
                    is TypeFilter -> filter.selected?.forEach { addQueryParameter("mcf_type[]", it) }
                    is StatusFilter -> filter.selected?.forEach { addQueryParameter("mcf_status[]", it) }
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("mcf_genre[]", it) }
                        filter.excluded?.forEach { addQueryParameter("mcf_genre_exclude[]", it) }
                    }
                    is YearRangeFilter -> {
                        filter.minValue?.let { addQueryParameter("mcf_year_from", it) }
                        filter.maxValue?.let { addQueryParameter("mcf_year_to", it) }
                    }
                    else -> {}
                }
            }

            if (filters == null) addQueryParameter("mcf_sort", sortBy)
            if (query?.isNotBlank() == true) addQueryParameter("mcf_search", query.trim())
        }.build()

        val mangas = parseArchive(client.get(url).asJsoup())
        return MangasPage(mangas, false)
    }

    // ============================ Filters =============================
    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$mangaSubString/").asJsoup()
        return FiltersData(
            genres = document.select(".mcf-genre-scroll .mcf-genre-item").mapNotNull { data ->
                val id = data.selectFirst("input")?.attr("value") ?: return@mapNotNull null
                val text = data.selectFirst("span")?.text() ?: return@mapNotNull null
                val count = document.selectFirst("a:contains($text) .count")?.text() ?: ""
                "$text $count" to id
            },
            translators = document.select(".mcf-team-select option").mapNotNull { data ->
                val id = data.attr("value")
                val text = data.text()
                if (id.isBlank() && text.isBlank()) return@mapNotNull null
                text to id
            },
            type = document.select(".mcf-section:contains(Тип) .mcf-check").mapNotNull { data ->
                val id = data.selectFirst("input")?.attr("value") ?: return@mapNotNull null
                val text = data.selectFirst("span")?.text() ?: return@mapNotNull null
                text to id
            },
            status = document.select(".mcf-section:contains(Статус) .mcf-check").mapNotNull { data ->
                val id = data.selectFirst("input")?.attr("value") ?: return@mapNotNull null
                val text = data.selectFirst("span")?.text() ?: return@mapNotNull null
                text to id
            },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(OrderBy(intl["order_by_filter_title"], orderByFilterOptions))
        data?.parseAs<FiltersData>()?.let {
            if (it.genres?.isNotEmpty() == true) filters.add(GenreFilter(intl["genre_filter_title"], it.genres))
            if (it.type?.isNotEmpty() == true) filters.add(TypeFilter("Тип", it.type))
            if (it.status?.isNotEmpty() == true) filters.add(StatusFilter("Статус", it.status))
            if (it.translators?.isNotEmpty() == true) filters.add(TranslatorsFilter("Команда перекладу", it.translators))
        }
        filters.add(AgeLimit("Вікове обмеження"))
        filters.add(YearRangeFilter())
        return FilterList(filters)
    }

    // ============================ Chapters =============================
    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val body = FormBody.Builder()
            .add("manga-core", mangaPath.trimEnd('/').substringAfterLast('/'))
            .add("manga_ajax", "1")
            .add("maction", "get_chapters")
            .build()

        val ajaxResponse = client.post("$baseUrl${mangaPath.trimEnd('/')}/ajax/chapters/", xhrHeaders, body)
            .asJsoup()

        val mainPage = mangaPage ?: client.get("$baseUrl$mangaPath").asJsoup()
        val json = mainPage.selectFirst("script:containsData(ManhvaChapterListUI)")?.data()
            ?: throw Exception("Manga data not found")

        val chapterData = json
            .substringAfter("ManhvaChapterListUI = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<ChapterDatesDto>()

        val hideLocked = hideLocked()

        return ajaxResponse.select(chapterListSelector()).mapNotNull { element ->
            chapterFromElement(element, mangaPath, chapterData, hideLocked)
        }
    }

    private fun chapterFromElement(element: Element, mangaPath: String, data: ChapterDatesDto?, hideLocked: Boolean): SChapter? {
        val chapter = super.chapterFromElement(element, mangaPath) ?: return null

        data?.chapterDates?.get(chapter.url)?.let { dateStr ->
            chapter.date_upload = parseChapterDate(dateStr)
        }

        val isLocked = data?.chapterAccess?.get(chapter.url)?.locked == true
        if (isLocked) {
            if (hideLocked) return null

            chapter.name = "🔒 ${chapter.name}"
            chapter.memo = buildJsonObject {
                chapter.memo.forEach { (key, value) -> put(key, value) }
                put("locked", "true")
            }
        }

        return chapter
    }

    // ============================ Pages =============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["locked"]?.string == "true") throw Exception("Цей розділ доступний лише з Преміум.")

        val chapterUrl = getChapterUrl(chapter)
        val data = client.get(chapterUrl).asJsoup()

        val json = data.selectFirst("script:containsData(window.MANHVA_READER_CONFIG)")?.data()
            ?: throw Exception("Chapter data not found")

        val dataJson = json
            .substringAfter("window.MANHVA_READER_CONFIG = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<PageJSON>()

        val imagesUrl = dataJson.endpoint.toHttpUrl().newBuilder().apply {
            addQueryParameter("post", dataJson.postId.toString())
            addQueryParameter("chapter", dataJson.chapterSlug)
            addQueryParameter("token", dataJson.token)
        }.build()

        val nonceHeaders = headersBuilder()
            .set("X-WP-Nonce", dataJson.restNonce)
            .build()

        val dataImages = client.get(imagesUrl, nonceHeaders).parseAs<Images>()

        return dataImages.pages.mapIndexed { index, string ->
            // url sets `Referrer` header to `chapterUrl`
            Page(index, url = chapterUrl, imageUrl = string)
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page)
        .newBuilder()
        .header("Referer", page.url)
        .header("Sec-Fetch-Dest", "image")
        .header("Sec-Fetch-Mode", "no-cors")
        .header("Sec-Fetch-Site", "same-site")
        .header("Sec-GPC", "1")
        .build()

    // ============================ Preferences =============================
    private fun hideLocked(): Boolean = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = HIDE_LOCKED_CHAPTERS_TITLE
            summary = HIDE_LOCKED_CHAPTERS_SUM
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_CHAPTERS = "hide_locked_chapters"
        private const val HIDE_LOCKED_CHAPTERS_TITLE = "Приховувати преміум глави"
        private const val HIDE_LOCKED_CHAPTERS_SUM = "Може викликати помилки при оновленні. Будуть відмічені іконкою: \uD83D\uDD12"
    }
}
