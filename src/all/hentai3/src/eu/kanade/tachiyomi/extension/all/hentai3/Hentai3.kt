package eu.kanade.tachiyomi.extension.all.hentai3

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getArtists
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getCodes
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getDescriptions
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getGroups
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getNumPages
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getTags
import eu.kanade.tachiyomi.extension.all.hentai3.Hentai3Utils.getTime
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Hentai3 :
    KeiSource(),
    ConfigurableSource {

    private val searchLang: String
        get() = when (lang) {
            "all" -> ""
            "en" -> "english"
            "ja" -> "japanese"
            "ko" -> "korean"
            "zh" -> "chinese"
            "es" -> "spanish"
            "pt" -> "portuguese"
            "id" -> "indonesian"
            "jv" -> "javanese"
            "tl" -> "tagalog"
            "vi" -> "vietnamese"
            "th" -> "thai"
            "my" -> "burmese"
            "tr" -> "turkish"
            "ru" -> "russian"
            "uk" -> "ukrainian"
            "pl" -> "polish"
            "de" -> "german"
            "it" -> "italian"
            "fr" -> "french"
            "nl" -> "dutch"
            "cs" -> "czech"
            "hu" -> "hungarian"
            "bg" -> "bulgarian"
            "is" -> "icelandic"
            "la" -> "latin"
            "ar" -> "arabic"
            "ceb" -> "cebuano"
            else -> ""
        }

    private val flagLang: String
        get() = when (lang) {
            "all" -> ""
            "en" -> "eng"
            "ja" -> "jpn"
            "ko" -> "kor"
            "zh" -> "zho"
            "es" -> "spa"
            "pt" -> "por"
            "id" -> "ind"
            "jv" -> "jav"
            "vi" -> "vie"
            "th" -> "tha"
            "tr" -> "tur"
            "ru" -> "rus"
            "uk" -> "ukr"
            "de" -> "deu"
            "it" -> "ita"
            "fr" -> "fra"
            "nl" -> "nld"
            "cs" -> "ces"
            "hu" -> "hun"
            "is" -> "isl"
            "la" -> "lat"
            "ar" -> "ara"
            "ceb" -> "ceb"
            else -> ""
        }

    private val prefs by getPreferencesLazy()

    // Popular + Latest
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (searchLang.isNotEmpty()) {
            "$baseUrl/language/$searchLang/${if (page > 1) page else ""}?sort=popular"
        } else {
            "$baseUrl/search?q=pages%3A>0&page=$page&sort=popular"
        }
        val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangasPage(doc)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (searchLang.isNotEmpty()) {
            "$baseUrl/language/$searchLang/$page"
        } else {
            "$baseUrl/search?q=pages%3A>0&page=$page"
        }
        val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangasPage(doc)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector).mapNotNull(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector) != null

        return MangasPage(mangas, hasNextPage)
    }

    private val popularMangaSelector = "a[href*=/d/]"
    private val popularMangaNextPageSelector = "a[rel=next]"

    private fun popularMangaFromElement(element: Element): SManga? {
        val title = element.selectFirst(".title")?.ownText() ?: return null
        return SManga.create().apply {
            this.title = title.shortenTitle()
            setUrlWithoutDomain(element.absUrl("href"))
            thumbnail_url = element.selectFirst(".cover img")?.getImage()
        }
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = when {
        query.startsWith(PREFIX_ID_SEARCH) -> MangasPage(listOf(searchMangaById(query.removePrefix(PREFIX_ID_SEARCH).trim())), false)
        else -> {
            val url = searchMangaRequestUrl(page, query, filters)
            parseMangasPage(client.get(url, Fetch.DOCUMENT).asJsoup())
        }
    }

    private suspend fun searchMangaById(id: String): SManga {
        if (id.toIntOrNull() == null) throw Exception("Incorrect ID")
        val document = client.get("$baseUrl/d/$id", Fetch.DOCUMENT).asJsoup()
        return parseMangaDetails(document)
    }

    private fun searchMangaRequestUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val queries = (
            listOfNotNull(
                query.replace("♀", "female").replace("♂", "male"),
                if (searchLang.isNotEmpty()) "language:$searchLang" else null,
            ) +
                combineQuery(filterList)
            )
            .joinToString(" ") { it.trim() }
            .trim()

        val offsetPage =
            filterList.firstInstanceOrNull<OffsetPageFilter>()?.state?.toIntOrNull()?.coerceAtLeast(0)?.plus(page) ?: page

        return "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", queries.ifEmpty { "pages:>0" })
            addQueryParameter("page", offsetPage.toString())
            filterList.firstInstanceOrNull<SelectFilter>()?.let { f ->
                addQueryParameter("sort", f.getValue())
            }
        }.build()
    }

    private fun combineQuery(filters: FilterList): List<String> {
        val advSearch = filters.filterIsInstance<TextFilter>().flatMap { filter ->
            val splits = filter.state.split(",")
                .map(String::trim)
                .filter(String::isNotBlank)
            splits.map { rawTag ->
                val tag = rawTag.lowercase()
                AdvSearchEntry(
                    type = filter.type,
                    text = tag.removePrefix("-"),
                    exclude = tag.startsWith("-"),
                    specific = filter.specific,
                )
            }
        }

        return advSearch
            .filter { tag -> tag.text.isNotBlank() }
            .map { tag ->
                buildString {
                    if (tag.exclude) append("-")
                    append(tag.type, ":")
                    // The site matches quoted page counts and upload ages as plain text
                    if (tag.type == "pages" || tag.type == "added") {
                        append(tag.text)
                    } else {
                        append("'", tag.text)
                        if (tag.specific.isNotBlank()) {
                            append(" (${tag.specific})")
                        }
                        append("'")
                    }
                }
            }
    }

    data class AdvSearchEntry(val type: String, val text: String, val exclude: Boolean, val specific: String)

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(showLanguage = searchLang.isEmpty())

    // Details + Chapters
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "d") return null

        return url.pathSegments.getOrNull(1)
            ?.takeIf { it.toIntOrNull() != null }
            ?.let { searchMangaById(it) }
    }

    private fun parseMangaDetails(document: Document): SManga {
        val fullTitle = document.select("#main-info h1").text()

        return SManga.create().apply {
            setUrlWithoutDomain(document.location())

            val authors = getGroups(document)
            val artists = getArtists(document)
            initialized = true

            title = fullTitle.shortenTitle()

            author = authors ?: artists
            artist = artists ?: authors

            val code = getCodes(document)
            // Some people want these additional details in description
            description = (if (title == fullTitle) "" else "Full title: $fullTitle\n\n")
                .plus(code ?: "")
                .plus(getNumPages(document)?.let { "Pages: $it\n" } ?: "")
                .plus(getDescriptions(document))
            genre = getTags(document)

            thumbnail_url = document.selectFirst("#main-info img")?.getImage()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun parseChapterList(document: Document, chapterUrl: String): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(chapterUrl)
            date_upload = getTime(document)
        },
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()

        val updatedManga = parseMangaDetails(document)
        val updatedChapters = parseChapterList(document, mangaUrl)

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // Related manga
    override val supportsRelatedMangas = true

    private fun relatedMangaListSelector(): String = if (flagLang.isNotEmpty()) {
        "#similar-content .doujin.flag-$flagLang a.cover, #similar-content .doujin:not(.flag) a.cover"
    } else {
        "#similar-content a.cover"
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(relatedMangaListSelector()).mapNotNull(::popularMangaFromElement)

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return doc.select("#thumbnail-gallery .single-thumb a > img")
            .mapIndexed { index, image ->
                Page(index, imageUrl = image.getImage())
            }
    }

    private fun Element.getImage(): String = attr("abs:data-src").ifEmpty { absUrl("src") }.replace("t.", ".")

    // Preferences
    private fun String.shortenTitle() = if (displayFullTitle) {
        trim()
    } else {
        replace(SHORT_TITLE_REGEX, "").trim().ifBlank { trim() }
    }

    private val displayFullTitle
        get() = prefs.getBoolean("full_title", false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = "full_title"
            title = "Display full title"
        }.also(screen::addPreference)
    }

    companion object {
        private val SHORT_TITLE_REGEX = Regex("""(\[(?:[^\[\]]|\[[^\[\]]*])*\]|[({][^)}]*[)}])""")
        private const val PREFIX_ID_SEARCH = "id:"
    }
}
