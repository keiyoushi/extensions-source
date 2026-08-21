package eu.kanade.tachiyomi.extension.en.novatoon

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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Novatoon :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val hideLockedChapters get() = preferences.getBoolean(HIDE_LOCKED_PREF, true)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF
            title = "Hide locked chapters"
            summary = "Hide chapters that are locked and cannot be read yet"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(mangaListUrl(page, order = "popular"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(mangaListUrl(page, order = "update"))

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchMangaList(searchUrl(page, query, filters))

    private fun mangaListUrl(page: Int, order: String): HttpUrl = "$baseUrl/manga/".toHttpUrl().newBuilder()
        .addQueryParameter("order", order)
        .apply {
            if (page > 1) {
                addPathSegments("page/$page/")
            }
        }
        .build()

    private fun searchUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        val builder = if (query.isBlank()) {
            "$baseUrl/manga/".toHttpUrl().newBuilder()
                .addFilterParams(filters)
        } else {
            "$baseUrl/".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
        }

        if (page > 1) {
            builder.addPathSegments("page/$page/")
        }

        return builder.build()
    }

    private fun HttpUrl.Builder.addFilterParams(filters: FilterList): HttpUrl.Builder = apply {
        for (filter in filters) {
            when (filter) {
                is UriPartFilter -> filter.selected.takeIf { it.isNotEmpty() }?.let { addQueryParameter(filter.param, it) }
                is GenreFilter -> filter.state.filter(GenreCheckBox::state).forEach { addQueryParameter("genre[]", it.value) }
                else -> {}
            }
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        OrderFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        GenreFilter(),
    )

    private suspend fun fetchMangaList(url: HttpUrl): MangasPage {
        val response = client.get(url, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        return response.use { document ->
            parseMangaList(document.asJsoup())
        }
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.listupd div.bsx").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("div.pagination a.next.page-numbers") != null ||
            document.selectFirst("div.hpage a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a[href]") ?: return null
        val url = link.absUrl("href")
        if (url.isBlank()) return null
        val title = element.selectFirst(".tt")?.text()?.takeIf { it.isNotEmpty() }
            ?: link.attr("title").takeIf { it.isNotEmpty() }
            ?: return null
        val thumbnail = element.selectFirst(".limit img")?.absUrl("src")
            .orEmpty()
            .ifEmpty { element.selectFirst(".limit img")?.absUrl("data-src").orEmpty() }
        return SManga.create().apply {
            setUrlWithoutDomain(url)
            this.title = title
            thumbnail_url = thumbnail
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = if (fetchDetails) parseMangaDetails(document, manga) else manga
        val updatedChapters = if (fetchChapters) parseChapterList(document) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "manga") return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain(url.toString())
        }
        return getMangaUpdate(manga, chapters = emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        title = document.selectFirst("h1.entry-title")?.text() ?: title
        thumbnail_url = document.selectFirst(".thumb img")?.absUrl("src")?.takeIf { it.isNotEmpty() } ?: thumbnail_url
        author = document.infoRow("Author")
        artist = document.infoRow("Artist")
        genre = document.select(".seriestugenre a").joinToString { it.text() }
        status = parseStatus(document.infoRow("Status").orEmpty())
        description = buildString {
            document.selectFirst(".entry-content-single")?.text()?.takeIf { it.isNotEmpty() }?.let(::append)
            document.infoRow("Alternative Names")?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nAlternative Names: $it")
            }
            document.infoRow("Released")?.takeIf { it.isNotEmpty() }?.let {
                append("\nReleased: $it")
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("#chapterlist ul li")
        .filterNot { hideLockedChapters && it.selectFirst("span.text-gold") != null }
        .mapNotNull { element ->
            val link = element.selectFirst(".eph-num a") ?: return@mapNotNull null
            val url = link.absUrl("href")
            val name = element.selectFirst(".chapternum")?.text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (url.isBlank()) return@mapNotNull null
            SChapter.create().apply {
                this.name = if (element.selectFirst("span.text-gold") != null) "$LOCK_ICON$name" else name
                setUrlWithoutDomain(url)
                date_upload = element.selectFirst(".chapterdate")?.text().orEmpty().let(::parseChapterDate)
            }
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = document.select("#readerarea img").mapIndexed { index, img ->
            val imageUrl = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
            Page(index, imageUrl = imageUrl)
        }
        if (pages.isEmpty() && document.selectFirst(".lock-container") != null) {
            throw Exception("This chapter is locked on Novatoon")
        }
        return pages
    }

    private fun parseChapterDate(raw: String): Long = runCatching {
        LocalDate.parse(raw, dateFormat).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun parseStatus(raw: String): Int {
        val status = raw.lowercase()
        return when {
            "ongoing" in status -> SManga.ONGOING
            "completed" in status -> SManga.COMPLETED
            "hiatus" in status -> SManga.ON_HIATUS
            "cancel" in status -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.infoRow(label: String): String? = select("table.infotable tr").firstOrNull { row ->
        row.selectFirst("td")?.text()?.equals(label, ignoreCase = true) == true
    }?.selectFirst("td + td")?.text()?.takeIf { it.isNotEmpty() }

    private companion object {
        val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
        const val HIDE_LOCKED_PREF = "hide_locked_chapters"
        const val LOCK_ICON = "\uD83D\uDD12 "
    }
}

private open class UriPartFilter(
    name: String,
    val param: String,
    private val parts: List<Pair<String, String>>,
) : Filter.Select<String>(name, parts.map { it.first }.toTypedArray()) {
    val selected: String get() = parts[state].second
}

private class OrderFilter :
    UriPartFilter(
        "Order",
        "order",
        listOf(
            "Default" to "",
            "A-Z" to "title",
            "Z-A" to "titlereverse",
            "Latest Update" to "update",
            "Latest Added" to "latest",
        ),
    )

private class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        listOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
        ),
    )

private class TypeFilter :
    UriPartFilter(
        "Type",
        "type",
        listOf(
            "All" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Comic" to "comic",
            "Novel" to "novel",
        ),
    )

private class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

private class GenreFilter :
    Filter.Group<GenreCheckBox>(
        "Genre",
        GENRES.map { GenreCheckBox(it.first, it.second) },
    )

private val GENRES = listOf(
    "Action" to "action",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Historical" to "historical",
    "Horror" to "horror",
    "Josei" to "josei",
    "Martial Arts" to "martial-arts",
    "Mecha" to "mecha",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Seinen" to "seinen",
    "Shounen" to "shounen",
    "Slice of Life" to "slice-of-life",
    "Supernatural" to "supernatural",
    "Tragedy" to "tragedy",
)
