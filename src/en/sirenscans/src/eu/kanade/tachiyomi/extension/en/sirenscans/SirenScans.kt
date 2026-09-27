package eu.kanade.tachiyomi.extension.en.sirenscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SirenScans :
    KeiSource(),
    ConfigurableSource {
    override val supportsFilterFetching = true
    private val preferences = getPreferences()

    // ========================= Preference =========================
    private val showLockedChapters: Boolean
        get() = preferences.getBoolean(SHOW_LOCKED_CHAPTERS_PREF, false)
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_PREF
            title = "Show locked chapters"
            summaryOn = "Locked chapters will be shown in the chapter list"
            summaryOff = "Locked chapters will be hidden from the chapter list"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // =========================Popular===========================
    // At this point the site only loads like 40 manga for latest, popular or any filter
    // so this will need to be updated if site starts using pagination
    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        return parseMangaList(client.get("$baseUrl/?browse=1&sort=popular").asJsoup())
    }

    // ==========================Latest============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        return parseMangaList(client.get("$baseUrl/?browse=1").asJsoup())
    }

    // ========================= Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val response = client.get(buildSearchMangaUrl(query, filters))
        return parseMangaList(response.asJsoup())
    }

    private fun buildSearchMangaUrl(query: String, filters: FilterList): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        if (query.isNotBlank()) {
            addQueryParameter("q", query)
        }
        addQueryParameter("browse", "1")

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    if (filter.state > 0) {
                        addQueryParameter("type", filter.selected)
                    }
                }

                is StatusFilter -> {
                    if (filter.state > 0) {
                        addQueryParameter("status", filter.selected)
                    }
                }

                is GenreFilter -> {
                    filter.state
                        .filterIsInstance<GenreCheckBox>()
                        .filter { it.state }
                        .forEach { addQueryParameter("tag[]", it.value) }
                }

                is SortFilter -> {
                    if (filter.state != 0) {
                        addQueryParameter("sort", filter.selected)
                    }
                }

                else -> {}
            }
        }
    }.build()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val newUrl = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(url.encodedPath)
            .encodedQuery(url.encodedQuery)
            .build()

        val response = client.get(newUrl)

        return parseMangaDetails(response.asJsoup()).apply {
            setUrlWithoutDomain(newUrl.encodedPath)
        }
    }

    // ========================= Details =========================
    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text().trim()

        description = document.selectFirst("p#series-desc")
            ?.text()
            ?.trim('"', ' ')
            ?.trim()

        thumbnail_url = document.selectFirst("div[style*=aspect-ratio] img")?.let {
            it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
        }

        val sidebarGenres = document.select("div.flex.flex-wrap.-mx-1 a[href*=tag]").eachText()
        if (sidebarGenres.isNotEmpty()) {
            genre = sidebarGenres.joinToString()
        }

        author = metaValue(document, "Author")
        artist = metaValue(document, "Artist")
        status = when (statValue(document, "Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun metaValue(document: Document, label: String): String? {
        val labelSpan = document.select("div.flex.items-center.justify-between span")
            .find { it.text().trim().equals(label, ignoreCase = true) }
        return labelSpan?.parent()?.nextElementSibling()?.text()?.trim()
    }

    private fun statValue(document: Document, label: String): String? {
        val labelDiv = document.select("div")
            .find { it.children().isEmpty() && it.text().trim().equals(label, ignoreCase = true) }
        return labelDiv?.nextElementSibling()?.select("span")?.last()?.text()?.trim()
    }

    // ========================= Chapters =========================
    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val chaptersListDiv = document.selectFirst("div#chapters-list") ?: return emptyList()
        val seriesUid = chaptersListDiv.attr("data-series-uid")
        val seriesSlug = chaptersListDiv.attr("data-series-slug")

        if (seriesUid.isBlank() || seriesSlug.isBlank()) return emptyList()

        val apiUrl = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("_chapters_html", "1")
            .addQueryParameter("series_uid", seriesUid)
            .addQueryParameter("series_slug", seriesSlug)
            .build()

        val json = client.get(apiUrl).parseAs<ChaptersResponseDto>()
        val rowsDocument = Jsoup.parseBodyFragment(json.rowsHtml, baseUrl)

        return rowsDocument.select("a.chapter-row[href]")
            .filter { showLockedChapters || it.attr("data-ch-locked") != "1" }
            .map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("abs:href"))
                    name = element.attr("data-ch-label").trim()
                    date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".ch-date-row span:last-child")?.text())
                }
            }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return parsePageList(document)
    }

    private fun parsePageList(document: Document): List<Page> = document
        .select("div#strip-reader img.reader-page")
        .mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }

    // ========================= Filters =========================
    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/?browse=1").asJsoup()
        return parseGenreData(document)
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = getGenreList(data)

        return FilterList(
            buildList {
                add(TypeFilter())
                add(StatusFilter())
                add(SortFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    // ========================= Helper =========================
    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.grid a.group[href]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.selectFirst("h2")!!.text().trim()
                thumbnail_url = element.selectFirst("img")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                }
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================= Companion Object =========================
    companion object {
        private const val SHOW_LOCKED_CHAPTERS_PREF = "pref_show_locked_chap"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    }
}
