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
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.math.roundToInt
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

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("coverage"), RatingFilter(preferences.rating)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("updated"), RatingFilter(preferences.rating)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return getCards(url)
    }

    private suspend fun getCards(url: HttpUrl): MangasPage {
        val document = client.get(url).asDocument()
        val mangas = document.select("li.card-item a.card-link").map { element ->
            SManga.create().apply {
                this.url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".card-title")!!.text()
                thumbnail_url = element.selectFirst("img.cover-img")?.absUrl("src")
                genre = element.selectFirst(".card-kind")?.textOrNull()
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null

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
            "read" -> client.get(url.newBuilder().query(null).build()).extractNextJs<ReaderPropsDto> {
                it is JsonObject && "chapter" in it && "series" in it
            }?.series?.id ?: return null
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
        val document = client.get(getMangaUrl(manga)).asDocument()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.series-title")!!.text()
            thumbnail_url = document.selectFirst(".series-cover img")?.absUrl("src")
            author = document.select(".series-credits a[href*=author=]").joinToString { it.text() }.ifEmpty { null }
            artist = document.select(".series-credits a[href*=artist=]").joinToString { it.text() }.ifEmpty { null }
            // Every genre and tag, including those behind the tag cloud's "+N more"
            val tags = document.extractNextJs<TagsDto> { it is JsonObject && "genres" in it && "tags" in it }!!
            genre = (document.select(".series-facts a[href*=kind=], .series-facts a[href*=demographic=]").map { it.text() } + tags.genres + tags.tags)
                .distinct().joinToString().ifEmpty { null }
            status = when (document.selectFirst(".fact-status")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            val rating = document.selectFirst(".series-numbers a.series-number-link")?.let { link ->
                val score = link.text().toFloatOrNull() ?: return@let null
                val stars = (score / 2).roundToInt()
                "★".repeat(stars) + "☆".repeat(5 - stars) + " ${link.text()}"
            }
            // Year, original language and content rating; type and demographic are in genres
            val facts = document.select(".series-facts > :not(.fact-status, a[href*=kind=], a[href*=demographic=])").map { it.text() }
            val altTitles = document.select("details.series-more li").map { it.text() }.distinct() - title
            val links = document.select(".tracker-row a").map { "[${it.attr("title")}](${it.absUrl("href")})" }
            description = listOfNotNull(
                (listOfNotNull(rating) + facts).joinToString(" · ").ifEmpty { null },
                document.select(".series-desc p").joinToString("\n\n") { it.toMarkdown().trim() }.ifEmpty { null },
                listOfNotNull(
                    altTitles.joinToString(" · ").ifEmpty { null }?.let { "**Alt titles:** $it" },
                    links.joinToString(" · ").ifEmpty { null }?.let { "**Links:** $it" },
                ).joinToString("\n").ifEmpty { null },
            ).joinToString("\n\n").ifEmpty { null }
        }
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val lastPage = document.select(".pagination-page a[href$=#chapters]").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val otherPages = coroutineScope {
            (2..lastPage).map { page ->
                async { client.get(mangaUrl.newBuilder().addQueryParameter("page", page.toString()).build()).asDocument() }
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
            val number = link.selectFirst(".ch-num")!!
            val title = link.selectFirst(".ch-title")!!
            // A plain title is the full display name, e.g. "Chapter 48" or an unnumbered extra.
            val chapterName = if (title.hasClass("is-plain")) title.text() else listOfNotNull(number.textOrNull(), title.text()).joinToString(": ")
            val chapterNumber = number.ownText().toFloatOrNull()

            val picked = Upload(
                SChapter.create().apply {
                    url = link.absUrl("href").toHttpUrl().pathSegments[1]
                    name = chapterName
                    chapterNumber?.let { chapter_number = it }
                    // Rows with a single upload use .ch-source, rows with several put the group in a dropdown trigger.
                    scanlator = element.selectFirst(".ch-source, .ch-source-name")?.textOrNull()
                    date_upload = Instant.tryParse(element.selectFirst("time.ch-time")?.attr("datetime"))
                },
                likes = element.selectFirst(".ch-likes")?.text()?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
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
        if (this == "just now") return System.currentTimeMillis()
        val match = TIME_AGO_REGEX.matchEntire(this ?: return 0L) ?: return 0L
        val index = TIME_AGO_UNITS.indexOfFirst { it.first == match.groupValues[2] }
        val unit = TIME_AGO_UNITS[index].second
        val count = match.groupValues[1].toInt()
        val upper = minOf(unit * (count + 1), TIME_AGO_UNITS.getOrNull(index - 1)?.second ?: Duration.INFINITE)
        return System.currentTimeMillis() - ((unit * count + upper) / 2).inWholeMilliseconds
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val pagesUrl = chapterUrl.newBuilder().addPathSegment("pages").build()
        val reader = client.get(pagesUrl).parseAs<ReaderDto>()

        // Relative /api/image/ paths or absolute CDN urls
        return reader.pages.mapIndexed { index, url ->
            Page(index, imageUrl = chapterUrl.resolve(url)!!.toString())
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
            summary = "Used for popular, latest and search. The Content rating filter overrides it.\nCurrent: %s"
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

    // Keep line breaks and links, descriptions render as Markdown.
    private fun Element.toMarkdown(): String = buildString {
        childNodes().forEach { node ->
            when (node) {
                is TextNode -> append(node.text())
                is Element -> when (node.normalName()) {
                    "br" -> append('\n')
                    "a" -> append("[${node.text()}](${node.absUrl("href")})")
                    "strong", "b" -> append("**${node.toMarkdown()}**")
                    else -> append(node.toMarkdown())
                }
            }
        }
    }

    companion object {
        private const val PREF_RATING = "pref_rating"
        private const val PREF_CHAPTER_LIST = "pref_chapter_list"
        private const val PREF_PREFERRED_GROUPS = "pref_preferred_groups"
        private const val PREF_HIDDEN_GROUPS = "pref_hidden_groups"

        private const val ONE_PER_GROUP = "group"
        private const val ONE_PER_CHAPTER = "chapter"

        // Groups the site marks as official translations
        private const val DEFAULT_PREFERRED_GROUPS = "Webtoon, Tapas, Tappytoon"

        private val TIME_AGO_REGEX = Regex("""(\d+)(mo|y|w|d|h|m) ago""")

        // Largest first, as the site's timeAgo() checks them.
        private val TIME_AGO_UNITS = listOf("y" to 365.days, "mo" to 30.days, "w" to 7.days, "d" to 1.days, "h" to 1.hours, "m" to 1.minutes)
    }
}
