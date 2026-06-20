package eu.kanade.tachiyomi.extension.all.nh

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.nh.NHUtils.getArtists
import eu.kanade.tachiyomi.extension.all.nh.NHUtils.getGroups
import eu.kanade.tachiyomi.extension.all.nh.NHUtils.getTagDescription
import eu.kanade.tachiyomi.extension.all.nh.NHUtils.getTags
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

open class NHentai(
    override val lang: String,
    private val nhLang: String,
) : HttpSource(),
    ConfigurableSource {

    final override val baseUrl = "https://nhentai.net"

    override val id by lazy { if (lang == "all") 7309872737163460316 else super.id }

    override val name = "NHentai"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client by lazy {
        network.client.newBuilder()
            .rateLimit(4)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")

    private var displayFullTitle: Boolean = when (preferences.getString(TITLE_PREF, "full")) {
        "full" -> true
        else -> false
    }

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("Full Title", "Short Title")
            entryValues = arrayOf("full", "short")
            summary = "%s"
            setDefaultValue("full")

            setOnPreferenceChangeListener { _, newValue ->
                displayFullTitle = when (newValue) {
                    "full" -> true
                    else -> false
                }
                true
            }
        }.also(screen::addPreference)
    }

    override fun latestUpdatesRequest(page: Int) = GET(if (nhLang.isBlank()) "$baseUrl/?page=$page" else "$baseUrl/language/$nhLang/?page=$page", headers)

    private fun latestUpdatesSelector() = "#content .container:not(.index-popular) .gallery"

    private fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        url = element.select("a").attr("href")
        title = element.select("a > div").text().replace("\"", "").let {
            if (displayFullTitle) it.trim() else it.shortenTitle()
        }
        thumbnail_url = element.selectFirst(".cover img")!!.let { img ->
            if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
        }
    }

    private fun latestUpdatesNextPageSelector() = "#content > section.pagination > a.next"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaRequest(page: Int) = GET(if (nhLang.isBlank()) "$baseUrl/search/?q=\"\"&sort=popular&page=$page" else "$baseUrl/language/$nhLang/popular?page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(PREFIX_ID_SEARCH) -> {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        }
        query.toIntOrNull() != null -> {
            client.newCall(searchMangaByIdRequest(query))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, query) }
        }
        else -> super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val nhLangSearch = if (nhLang.isBlank()) "" else "language:$nhLang "
        val advQuery = combineQuery(filterList)
        val favoriteFilter = filterList.findInstance<FavoriteFilter>()
        val offsetPage =
            filterList.findInstance<OffsetPageFilter>()?.state?.toIntOrNull()?.plus(page) ?: page

        if (favoriteFilter?.state == true) {
            val url = "$baseUrl/favorites/".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$query $advQuery")
                .addQueryParameter("page", offsetPage.toString())

            return GET(url.build(), headers)
        } else {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder()
                // Blank query (Multi + sort by popular month/week/day) shows a 404 page
                // Searching for `""` is a hacky way to return everything without any filtering
                .addQueryParameter("q", "$query $nhLangSearch$advQuery".ifBlank { "\"\"" })
                .addQueryParameter("page", offsetPage.toString())

            filterList.findInstance<SortFilter>()?.let { f ->
                url.addQueryParameter("sort", f.toUriPart())
            }

            return GET(url.build(), headers)
        }
    }

    private fun combineQuery(filters: FilterList): String = buildString {
        filters.filterIsInstance<AdvSearchEntryFilter>().forEach { filter ->
            filter.state.split(",")
                .map(String::trim)
                .filterNot(String::isBlank)
                .forEach { tag ->
                    val y = !(filter.name == "Pages" || filter.name == "Uploaded")
                    if (tag.startsWith("-")) append("-")
                    append(filter.name, ':')
                    if (y) append('"')
                    append(tag.removePrefix("-"))
                    if (y) append('"')
                    append(" ")
                }
        }
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.toString().contains("/login/") &&
            document.select(".fa-sign-in").isNotEmpty()
        ) {
            throw Exception("Log in via WebView to view favorites")
        }
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val tags = document.parseTags()

        val fullTitle = listOfNotNull(
            document.selectFirst("h1 .before")?.text(),
            document.selectFirst("h1 .pretty")?.text(),
            document.selectFirst("h1 .after")?.text(),
        ).joinToString("").trim()
        val prettyTitle = document.selectFirst("h1 .pretty")?.text()?.trim() ?: fullTitle
        val japaneseTitle = document.selectFirst("h2.title")?.text()?.trim()
        val pages = document.select(".tag-container.field-name")
            .firstOrNull { "pages" in it.ownText().lowercase() }
            ?.selectFirst(".name")?.text() ?: "?"
        val favorites = document.selectFirst(".btn-primary .nobold")?.text()
            ?.trim()?.removeSurrounding("(", ")") ?: "?"

        return SManga.create().apply {
            title = if (displayFullTitle) fullTitle else prettyTitle
            thumbnail_url = document.selectFirst("#cover img")?.let { img ->
                if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
            }
            status = SManga.COMPLETED
            artist = getArtists(tags)
            author = getGroups(tags) ?: getArtists(tags)
            description = buildString {
                append(fullTitle, "\n")
                if (!japaneseTitle.isNullOrBlank()) append(japaneseTitle, "\n")
                append("\n")
                append("Pages: $pages\n")
                append("Favorited by: $favorites\n")
                append(getTagDescription(tags))
            }
            genre = getTags(tags)
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val tags = document.parseTags()
        val uploadDate = document.selectFirst("time[datetime]")?.attr("datetime")
            ?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() } ?: 0L

        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                scanlator = getGroups(tags)
                date_upload = uploadDate
                url = response.request.url.encodedPath
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        // The new SvelteKit site embeds page thumbnails directly in the gallery HTML.
        // Thumbnail URL pattern: https://t4.nhentai.net/galleries/{media_id}/{page}t.jpg
        // Full image URL pattern: https://i4.nhentai.net/galleries/{media_id}/{page}.jpg
        val cdnPattern = Regex("""//t(\d+)\.nhentai""")
        val thumbPattern = Regex("""(\d+)t\.(\w+)$""")

        return response.asJsoup()
            .select("#thumbnail-container .thumb-container img")
            .mapIndexed { i, img ->
                val thumbSrc = img.attr("src")
                val imageUrl = cdnPattern.replace(thumbSrc) { "//i${it.groupValues[1]}.nhentai" }
                    .let { thumbPattern.replace(it) { m -> "${m.groupValues[1]}.${m.groupValues[2]}" } }
                Page(index = i, imageUrl = imageUrl)
            }
    }

    private fun Document.parseTags(): List<Tag> {
        return select("#tags .tag-container.field-name").flatMap { container ->
            val type = when (container.ownText().trim().lowercase().removeSuffix(":").trim()) {
                "artists" -> "artist"
                "groups" -> "group"
                "characters" -> "character"
                "parodies" -> "parody"
                "tags" -> "tag"
                "languages" -> "language"
                "categories" -> "category"
                else -> return@flatMap emptyList()
            }
            container.select("a[href] .name").mapNotNull { el ->
                el.text().trim().takeIf { it.isNotBlank() }?.let { Tag(name = it, type = type) }
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TagFilter(),
        CategoryFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        Filter.Header("Uploaded valid units are h, d, w, m, y."),
        Filter.Header("example: (>20d)"),
        UploadedFilter(),
        Filter.Header("Filter by pages, for example: (>20)"),
        PagesFilter(),

        Filter.Separator(),
        SortFilter(),
        OffsetPageFilter(),
        Filter.Header("Sort is ignored if favorites only"),
        FavoriteFilter(),
    )

    class TagFilter : AdvSearchEntryFilter("Tags")
    class CategoryFilter : AdvSearchEntryFilter("Categories")
    class GroupFilter : AdvSearchEntryFilter("Groups")
    class ArtistFilter : AdvSearchEntryFilter("Artists")
    class ParodyFilter : AdvSearchEntryFilter("Parodies")
    class CharactersFilter : AdvSearchEntryFilter("Characters")
    class UploadedFilter : AdvSearchEntryFilter("Uploaded")
    class PagesFilter : AdvSearchEntryFilter("Pages")
    open class AdvSearchEntryFilter(name: String) : Filter.Text(name)

    class OffsetPageFilter : Filter.Text("Offset results by # pages")

    private class FavoriteFilter : Filter.CheckBox("Show favorites only", false)

    private class SortFilter :
        UriPartFilter(
            "Sort By",
            arrayOf(
                Pair("Popular: All Time", "popular"),
                Pair("Popular: Month", "popular-month"),
                Pair("Popular: Week", "popular-week"),
                Pair("Popular: Today", "popular-today"),
                Pair("Recent", "date"),
            ),
        )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private const val TITLE_PREF = "Display manga title as:"
    }
}
