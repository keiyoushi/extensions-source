package eu.kanade.tachiyomi.extension.en.mangarawclub

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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

// Site uses AP style months ("Sept.", "March") and optional minutes ("3 p.m.")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("[MMMM][MMM] d, yyyy, h[:mm] a")
    .toFormatter(Locale.ENGLISH)

@Source
abstract class MangaRawClub :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(10.seconds)
        .readTimeout(30.seconds)

    private val preferences by getPreferencesLazy()
    private fun nsfw() = preferences.getBoolean(PREF_HIDE_NSFW, false)

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage = browseMangaList("$baseUrl/browse-comics/data/?page=$page&sort=popular_all_time&safe_mode=${if (nsfw()) "1" else "0"}".toHttpUrl())

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = browseMangaList("$baseUrl/browse-comics/data/?page=$page&sort=latest&safe_mode=${if (nsfw()) "1" else "0"}".toHttpUrl())

    // ============================== Search ===============================
    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Fallback directly to autocomplete query if only search term is provided
        if (query.isNotBlank() && filters.all { it.isDefault() }) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
                addQueryParameter("search", query.trim())
                addQueryParameter("results", page.toString())
            }.build()

            val document = client.get(url).asJsoup()
            val mangas = document.select(".novel-item").map { element ->
                SManga.create().apply {
                    title = element.selectFirst(".novel-title")!!.text()
                    thumbnail_url = element.selectFirst(".novel-cover img")?.let {
                        it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                    }
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                }
            }
            val hasNextPage = document.selectFirst("nav.paging a:contains(Next)") != null
            return MangasPage(mangas, hasNextPage)
        }

        val url = "$baseUrl/browse-comics/data/".toHttpUrl().newBuilder().apply {
            val tagsIncl = mutableListOf<String>()
            val genreIncl = mutableListOf<String>()
            val genreExcl = mutableListOf<String>()

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.selected)
                    is GenreFilter -> {
                        filter.state.forEach {
                            when {
                                it.isIncluded() -> genreIncl.add(it.name)
                                it.isExcluded() -> genreExcl.add(it.name)
                            }
                        }
                    }
                    is StatusFilter -> addQueryParameter("status", filter.selected)
                    is TypeFilter -> addQueryParameter("type", filter.selected)
                    is ChapterMinFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("min_chapters", filter.state.trim())
                    }
                    is ChapterMaxFilter -> {
                        if (filter.state.isNotEmpty()) addQueryParameter("max_chapters", filter.state.trim())
                    }
                    is RatingFilter -> {
                        if (filter.state.isNotEmpty()) {
                            val value = filter.state.toDoubleOrNull() ?: 0.0
                            addQueryParameter("min_rating", (value * 10).toInt().toString())
                        }
                    }
                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            filter.state.split(",").filter { it.isNotEmpty() }.forEach { tag ->
                                tagsIncl.add(tag.trim())
                            }
                        }
                    }
                    is ExtraFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter(it.value, "1")
                        }
                    }
                    else -> {}
                }
            }

            addQueryParameter("safe_mode", if (nsfw()) "1" else "0")
            addQueryParameter("page", page.toString())
            if (genreIncl.isNotEmpty()) addQueryParameter("include_genres", genreIncl.joinToString(","))
            if (genreExcl.isNotEmpty()) addQueryParameter("exclude_genres", genreExcl.joinToString(","))
            if (tagsIncl.isNotEmpty()) addQueryParameter("tags", tagsIncl.joinToString(","))
            addQueryParameter("q", query)
        }.build()

        return browseMangaList(url)
    }

    private suspend fun browseMangaList(url: HttpUrl): MangasPage {
        val data = client.get(url).parseAs<Dto>()
        val document = Jsoup.parseBodyFragment(data.html, baseUrl)
        val mangas = document.select(".comic-card").map { searchMangaFromElement(it) }

        return MangasPage(mangas, data.hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".comic-card__title a")!!.text()
        thumbnail_url = element.selectFirst(".comic-card__cover img")?.let {
            it.absUrl("data-src").ifEmpty { it.absUrl("src") }
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga = SManga.create().apply {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        document.selectFirst(".novel-header") ?: throw Exception("Page not found")

        url = manga.url
        title = manga.title
        author = document.selectFirst(".author a")?.attr("title")?.trim()?.takeIf { it.lowercase() != "updating" }

        description = buildString {
            document.selectFirst(".description")?.text()?.substringAfter("Summary is")?.let {
                append(it)
            }

            parseAltNames(document.selectFirst(".alternative-title")?.ownText())?.let { altNames ->
                if (isNotEmpty()) append("\n\n")
                append(ALT_NAME)
                altNames.forEach { name -> append("\n- $name") }
            }
        }

        genre = document.select(".categories a[href*=genre]").joinToString {
            it.ownText().split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
        }

        status = when {
            document.selectFirst("div.header-stats strong.completed") != null -> SManga.COMPLETED
            document.selectFirst("div.header-stats strong.ongoing") != null -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst(".cover img")?.let {
            it.absUrl("data-src").ifEmpty { it.absUrl("src") }
        }
    }

    private fun parseAltNames(raw: String?): List<String>? {
        if (raw.isNullOrEmpty()) return null

        val separator = if (ALT_NAME_BULLET_SEMICOLON_REGEX.containsMatchIn(raw)) {
            ALT_NAME_BULLET_SEMICOLON_REGEX
        } else {
            ALT_NAME_COMMA_REGEX
        }

        return raw.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() != "updating" }
            .takeIf { it.isNotEmpty() }
    }

    // ============================= Chapters ==============================
    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val document = client.get(baseUrl + manga.url + "all-chapters/").asJsoup()
        return document.select("ul.chapter-list > li").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))

                val chapterName = element.selectFirst(".chapter-title, .chapter-number")!!.ownText().removeSuffix("-eng-li")
                name = "Chapter $chapterName"

                date_upload = parseChapterDate(element.selectFirst(".chapter-update")?.attr("datetime"))
            }
        }
    }

    private fun parseChapterDate(string: String?): Long {
        if (string.isNullOrEmpty()) return 0L
        val date = string.replace(".", "").replace("Sept", "Sep")
        return DATE_FORMATTER.tryParseDateTime(date)
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#chapter-reader img")
            .filterNot { it.absUrl("src").contains("credits-mgeko.png") }
            .mapIndexed { i, img ->
                Page(i, imageUrl = img.absUrl("src"))
            }
    }

    // ============================= Utilities =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_NSFW
            title = "Hide NSFW"
            summary = "Hides NSFW entries"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val ALT_NAME = "Alternative Names:"
        private const val PREF_HIDE_NSFW = "pref_hide_nsfw"
        private val ALT_NAME_BULLET_SEMICOLON_REGEX = Regex("[•;]")
        private val ALT_NAME_COMMA_REGEX = Regex(",")
    }
}
