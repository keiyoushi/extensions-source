package eu.kanade.tachiyomi.extension.en.webnex

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MangaMeta
import keiyoushi.utils.asJsoup
import keiyoushi.utils.describe
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class Webnex :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::seriesCookieInterceptor)

    // "Opening series" page: sets wnx_series from a script, then reloads
    private fun seriesCookieInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.body.contentType()?.subtype != "html") return response
        val body = response.peekBody(4096).string()
        if ("<title>Opening series</title>" !in body) return response
        response.close()
        if (!saveScriptCookie(response.request.url, body)) throw IOException("Open in WebView to load the series")
        return chain.proceed(chain.request())
    }

    // Either "name=value; Path=..." or ["name=value", "Path=...", ...].join("; ")
    private fun saveScriptCookie(url: HttpUrl, html: String): Boolean {
        val match = SCRIPT_COOKIE_REGEX.find(html) ?: return false
        val cookie = Cookie.parse(url, STRING_LITERAL_REGEX.findAll(match.value).joinToString("; ") { it.groupValues[1] }) ?: return false
        client.cookieJar.saveFromResponse(url, listOf(cookie))
        return true
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("coverage"), RatingFilter(preferences.rating)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCards(
        baseUrl.toHttpUrl().newBuilder().addQueryParameter("latest", page.toString()).build(),
        "section.latest-section",
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return getCards(url)
    }

    private suspend fun getCards(url: HttpUrl, section: String = ""): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asDocument()
        val mangas = document.select("$section li.card-item a.card-link").map { element ->
            SManga.create().apply {
                this.url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".card-title")!!.text()
                thumbnail_url = element.selectFirst("img.cover-img")?.absUrl("src")
                genre = element.selectFirst(".card-kind")?.textOrNull()
            }
        }
        val hasNextPage = document.selectFirst("a[aria-label=Next page]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        return when (url.pathSegments[0]) {
            "browse" -> getCards(url.newBuilder().setQueryParameter("page", page.toString()).build())
            "groups" -> getCards(
                url.newBuilder().query(null)
                    .addQueryParameter("tab", "series")
                    .addQueryParameter("page", page.toString())
                    .build(),
            )
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = when (url.pathSegments[0]) {
            "manga" -> url.pathSegments.getOrNull(1) ?: return null
            "read" -> client.get(url.newBuilder().query(null).build(), Fetch.DOCUMENT).asJsoup().readerProps()
                ?.series?.href?.let { baseUrl.toHttpUrl().resolve(it)!!.pathSegments[1] } ?: return null
            else -> return null
        }

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = slug },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asDocument()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.series-title")!!.text()
            thumbnail_url = document.selectFirst(".series-top-cover img")?.absUrl("src")
            author = document.select(".series-credits a[href*=author=]").joinToString { it.text() }.ifEmpty { null }
            artist = document.select(".series-credits a[href*=artist=]").joinToString { it.text() }.ifEmpty { null }
            // Every genre and tag, including those behind the tag cloud's "+N more"
            val tags = document.extractNextJs<TagsDto> { it is JsonObject && "genres" in it && "tags" in it }!!
            val seriesType = document.selectFirst(".series-badges a[href*=kind=]")?.text()
            val demographic = document.selectFirst(".series-badges a[href*=demographic=]")?.text()
            genre = (listOfNotNull(seriesType, demographic) + tags.genres + tags.tags).distinct().joinToString().ifEmpty { null }
            status = when (document.selectFirst(".series-badges li:has(.dot)")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            // Year, original language and content rating; type and demographic are in genres
            val facts = document.select(".series-badges > li:not(:has(.dot, a[href*=kind=], a[href*=demographic=]))").map { it.text() }
            val year = facts.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { year -> year in 1900..2100 } }
            val language = facts.firstNotNullOfOrNull(LANGUAGES::get)
            val contentRating = facts.firstNotNullOfOrNull { CONTENT_RATINGS[it.lowercase()] }
            describe {
                rating(document.selectFirst(".cover-score strong")?.text()?.toDoubleOrNull())
                facts.forEach { fact ->
                    val label = when {
                        year != null && fact.toIntOrNull() == year -> "Year"
                        fact in LANGUAGES -> "Original language"
                        CONTENT_RATINGS[fact.lowercase()] != null -> "Content rating"
                        else -> null
                    }
                    if (label != null) field(label, fact, summary = true) else info(fact)
                }
                // A copy, so the official edition links can be taken out of the synopsis
                val synopsis = document.selectFirst(".series-desc")?.clone()
                val editions = synopsis?.takeOfficialLinks().orEmpty()
                synopsis?.select("p")?.forEach { description(it) }
                editions.forEach { (label, runLinks) -> links(runLinks, label) }
                altTitles(document.select(".series-alt-name").map { it.text() }.filter { it != title }.distinct())
                val (trackerPages, otherLinks) = document.select(".tracker-row a").partition { MangaMeta.Tracker.fromUrl(it.absUrl("href")) != null }
                trackers(trackerPages.map { it.absUrl("href") })
                links(otherLinks.map { it.attr("title") to it.absUrl("href") })
                meta {
                    field("Type", seriesType, summary = true)
                    field("Demographic", demographic)
                    this.contentRating = contentRating
                    // The page's genre and tag chips; the payload's other names sit behind the tag cloud's "+N more"
                    val pageGenres = document.select("a[href*=?genre=]").map { it.text() }
                    val pageTags = document.select("a[href*=?tag=]").map { it.text() }
                    val onPage = (pageGenres + pageTags).map(String::lowercase).toSet()
                    primaryTags = pageGenres
                    chips("Genres", pageGenres)
                    chips("Tags", pageTags)
                    chips("More tags", (tags.genres + tags.tags).filterNot { it.lowercase() in onPage }.distinctBy(String::lowercase), collapsed = true)
                }
            }
        }
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val lastPage = document.select(".pagination-page a[href$=#chapters]").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val otherPages = coroutineScope {
            (2..lastPage).map { page ->
                async { client.get(mangaUrl.newBuilder().addQueryParameter("page", page.toString()).build(), Fetch.DOCUMENT).asDocument() }
            }.awaitAll()
        }

        // Dropdown uploads only have a relative time, so keep the date estimated when they were first seen.
        val knownDates = chapters.associate { it.url to it.date_upload }
        return SMangaUpdate(details, (listOf(document) + otherPages).flatMap { parseChapters(it, knownDates) })
    }

    // Each row is one chapter: the upload the site picked, plus the other groups' uploads in its dropdown.
    private fun parseChapters(document: Document, knownDates: Map<String, Long>): List<SChapter> {
        val hiddenGroups = commaList(PREF_HIDDEN_GROUPS, "")
        val preferredGroups = commaList(PREF_PREFERRED_GROUPS, DEFAULT_PREFERRED_GROUPS)
        val onePerChapter = preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER

        // Hidden rows are decoys the site's own page never shows
        return document.select("li.ch-row:not([aria-hidden=true]):not([style*=display:none])").flatMap { element ->
            val link = element.selectFirst("a.ch-link")!!
            val number = link.selectFirst(".ch-num")!!.ownText()
            val chapterName = link.selectFirst(".ch-title")?.let { "Chapter $number: ${it.text()}" } ?: "Chapter $number"
            val chapterNumber = number.toFloatOrNull()

            val picked = Upload(
                SChapter.create().apply {
                    url = link.absUrl("href").toHttpUrl().pathSegments[1]
                    name = chapterName
                    chapterNumber?.let { chapter_number = it }
                    // Rows with a single upload use .ch-source, rows with several put the group in a dropdown trigger.
                    scanlator = element.selectFirst(".ch-source, .ch-source-name")?.textOrNull()
                    date_upload = Instant.tryParse(element.selectFirst("time.ch-time")?.attr("datetime"))
                },
                likes = element.selectFirst(".ch-likes")?.text()?.filter { it in '0'..'9' }?.toIntOrNull() ?: 0,
                warning = false,
            )
            val others = element.select(".ch-sources-menu a.ch-sources-item").map { item ->
                Upload(
                    SChapter.create().apply {
                        url = item.absUrl("href").toHttpUrl().pathSegments[1]
                        name = chapterName
                        chapterNumber?.let { chapter_number = it }
                        scanlator = item.selectFirst(".ch-sources-name")?.textOrNull()
                        date_upload = knownDates[url]?.takeIf { it != 0L } ?: item.selectFirst(".ch-sources-time")?.text().parseTimeAgo()
                    },
                    likes = item.selectFirst(".ch-sources-likes")?.text()?.toIntOrNull() ?: 0,
                    // The site's warning dot, drawn only on the dropdown's other uploads
                    warning = item.selectFirst(".dot-warn") != null,
                )
            }

            val uploads = (listOf(picked) + others).filterNot { it.isFromGroup(hiddenGroups) }
            if (!onePerChapter) return@flatMap uploads.map { it.chapter }

            // Ties keep the site's pick, listed first
            listOfNotNull(
                uploads.sortedWith(
                    compareBy<Upload>({ it.warning }, { upload -> preferredGroups.rankOf { upload.isFromGroup(listOf(it)) } })
                        .thenByDescending { it.likes },
                ).firstOrNull()?.chapter,
            )
        }
    }

    private class Upload(val chapter: SChapter, val likes: Int, val warning: Boolean) {
        fun isFromGroup(groups: List<String>) = groups.any { it.equals(chapter.scanlator, ignoreCase = true) }
    }

    // Inverts the site's timeAgo(): "4w ago" means 4 weeks up to the 30 days where "1mo" takes over, so take the middle.
    private fun String?.parseTimeAgo(): Long {
        if (this == "just now") return Clock.System.now().toEpochMilliseconds()
        val match = TIME_AGO_REGEX.matchEntire(this ?: return 0L) ?: return 0L
        val index = TIME_AGO_UNITS.indexOfFirst { it.first == match.groupValues[2] }
        val unit = TIME_AGO_UNITS[index].second
        val count = match.groupValues[1].toInt()
        val upper = minOf(unit * (count + 1), TIME_AGO_UNITS.getOrNull(index - 1)?.second ?: Duration.INFINITE)
        return (Clock.System.now() - (unit * count + upper) / 2).toEpochMilliseconds()
    }

    private fun Document.readerProps() = extractNextJs<ReaderPropsDto> { it is JsonObject && "chapter" in it && "series" in it }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter), Fetch.DOCUMENT)
        // The page list needs the cookie the reader page sets from a script, on its final url after redirects
        val readerUrl = response.request.url
        val html = response.body.string()
        saveScriptCookie(readerUrl, html)
        val props = html.asJsoup(readerUrl.toString()).readerProps()!!
        val reader = client.get(readerUrl.resolve(props.pagesUrl)!!, Fetch.XHR.from(readerUrl)).parseAs<ReaderDto>()

        // Relative /api/image/ paths or absolute CDN urls
        return reader.pages.mapIndexed { index, url ->
            Page(index, imageUrl = readerUrl.resolve(url)!!.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        KindFilter(),
        StatusFilter(),
        DemographicFilter(),
        LanguageFilter(),
        RatingFilter(preferences.rating),
        ChaptersFilter(),
        MatchFilter(),
        GenreFilter(),
        FormatFilter(),
        ThemeFilter(),
        TagsFilter(),
        NumberFilter("Year from", "from"),
        NumberFilter("Year to", "to"),
        TextFilter("Author", "author"),
        TextFilter("Artist", "artist"),
        TextFilter("Studio", "studio"),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_RATING
            title = "Content rating"
            summary = "Used for popular and search. The Content rating filter overrides it.\nCurrent: %s"
            entries = RATING_OPTIONS.map { it.first }.toTypedArray()
            entryValues = RATING_OPTIONS.map { it.second }.toTypedArray()
            setDefaultValue("")
        }.let(screen::addPreference)

        val preferredGroups = EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Preferred groups"
            summary = "Group names, comma-separated. One per chapter shows the first listed group that has an upload without the site's warning mark. " +
                "Default: $DEFAULT_PREFERRED_GROUPS, the site's official translations"
            dialogTitle = "Preferred groups"
            setDefaultValue(DEFAULT_PREFERRED_GROUPS)
            setEnabled(preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER)
        }

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_LIST
            title = "Chapter list"
            entries = arrayOf("One per group, like the site's list", "One per chapter")
            entryValues = arrayOf(ONE_PER_GROUP, ONE_PER_CHAPTER)
            summary = "%s"
            setDefaultValue(ONE_PER_GROUP)
            setOnPreferenceChangeListener { _, newValue ->
                preferredGroups.setEnabled(newValue == ONE_PER_CHAPTER)
                true
            }
        }.let(screen::addPreference)

        screen.addPreference(preferredGroups)

        EditTextPreference(screen.context).apply {
            key = PREF_HIDDEN_GROUPS
            title = "Hidden groups"
            summary = "Group names, comma-separated. Their uploads are left out of the chapter list"
            dialogTitle = "Hidden groups"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    private fun commaList(key: String, default: String) = preferences.getString(key, default).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    private fun List<String>.rankOf(matches: (String) -> Boolean) = indexOfFirst(matches).takeIf { it >= 0 } ?: size

    private val SharedPreferences.rating
        get() = getString(PREF_RATING, "")!!

    // Next.js streams late parts of the page as hidden chunks that client JS moves into <template> placeholders.
    private fun Response.asDocument(): Document = asJsoup().apply {
        select("div[hidden][id^=S:]").forEach { chunk ->
            val placeholder = getElementById("P:${chunk.id().removePrefix("S:")}") ?: return@forEach
            placeholder.parent()!!.insertChildren(placeholder.siblingIndex(), chunk.childNodes())
            placeholder.remove()
            chunk.remove()
        }
    }

    // Removes the synopsis' runs of an "Original Webtoon:" or "Official Translations:" bold label and its comma-separated links, and returns each label with its links
    private fun Element.takeOfficialLinks(): List<Pair<String, List<Pair<String, String>>>> = select("strong").filter { OFFICIAL_LABEL.matches(it.text()) }.mapNotNull { strong ->
        var linked = false
        // A <br> may sit between the label and its links; after a link it ends the run
        val run = generateSequence(strong.nextSibling()) { it.nextSibling() }
            .takeWhile { node ->
                when (node) {
                    is Element -> (node.tagName() == "a").also { if (it) linked = true } || (node.tagName() == "br" && !linked)
                    is TextNode -> node.wholeText.trim().let { it.isEmpty() || it == "," }
                    else -> false
                }
            }
            .toList()
        val links = run.filterIsInstance<Element>().filter { it.tagName() == "a" && it.absUrl("href").isNotEmpty() }
            .map { it.text() to it.absUrl("href") }
        // Only a label whose links end the line is taken out; one followed by more text stays as it is
        val end = (run.lastOrNull() ?: strong).nextSibling()
        val clean = end == null || (end is Element && (end.tagName() == "strong" || end.tagName() == "br"))
        if (links.isEmpty() || !clean) return@mapNotNull null
        (run + strong).forEach(Node::remove)
        strong.text().removeSuffix(":") to links
    }

    companion object {
        private val OFFICIAL_LABEL = Regex("""^(?:Original\b.*|Official\b.*Translations?):$""", RegexOption.IGNORE_CASE)

        private val LANGUAGES = ORIGINAL_LANGUAGES.toMap()
        private val CONTENT_RATINGS = mapOf(
            "safe" to MangaMeta.ContentRating.SAFE,
            "suggestive" to MangaMeta.ContentRating.SUGGESTIVE,
            "erotica" to MangaMeta.ContentRating.ADULT,
            "pornographic" to MangaMeta.ContentRating.ADULT,
        )
        private const val PREF_RATING = "pref_rating"
        private const val PREF_CHAPTER_LIST = "pref_chapter_list"
        private const val PREF_PREFERRED_GROUPS = "pref_preferred_groups"
        private const val PREF_HIDDEN_GROUPS = "pref_hidden_groups"

        private const val ONE_PER_GROUP = "group"
        private const val ONE_PER_CHAPTER = "chapter"

        // Groups the site marks as official translations
        private const val DEFAULT_PREFERRED_GROUPS = "Webtoon, Tapas, Tappytoon"

        private val SCRIPT_COOKIE_REGEX = Regex(""""wnx_\w+=[^"]+"(?:\s*,\s*"[^"]*")*""")
        private val STRING_LITERAL_REGEX = Regex(""""([^"]*)"""")

        private val TIME_AGO_REGEX = Regex("""(\d+)(mo|y|w|d|h|m) ago""")

        // Largest first, as the site's timeAgo() checks them.
        private val TIME_AGO_UNITS = listOf("y" to 365.days, "mo" to 30.days, "w" to 7.days, "d" to 1.days, "h" to 1.hours, "m" to 1.minutes)
    }
}
