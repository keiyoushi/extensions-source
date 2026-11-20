package keiyoushi.utils

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode

internal const val MEMO_META = "meta"
internal const val META_VERSION = 1

/**
 * Sets [SManga.description], as Markdown, and stores the same facts in `memo["meta"]` as typed keys and display
 * blocks (`sections`), keeping the other memo keys. An app that draws the blocks itself lists `sections` in
 * `AppInfo.getShownMetaKeys()`, and the description then keeps only what has no block: the synopsis and [MangaDescription.info].
 *
 * Sections come in call order, separated by a blank line, except a run of [MangaDescription.info] and
 * [MangaDescription.field], which share one line. Site text is escaped; null, blank and empty input adds nothing.
 *
 * ```
 * describe {
 *     rating(score, votes = votes)
 *     field("Type", type, summary = true)
 *     separator()
 *     description(document.selectFirst(".synopsis")) // or plain text
 *     field("Publisher", publisher)
 *     altTitles(altTitles)
 *     links(listOf("AniList" to anilistUrl, "MyAnimeList" to malUrl))
 *     meta { chips("Themes", themes) } // blocks the description doesn't show
 * }
 * ```
 */
fun SManga.describe(block: MangaDescription.() -> Unit) = describe(shownMetaKeys(), block)

internal fun SManga.describe(shown: Set<String>, block: MangaDescription.() -> Unit) {
    val builder = MangaDescription(shown).apply(block)
    description = builder.build()
    memo = memo.withMeta(builder.facts.toJson(titleOrNull()))
}

/**
 * Stores [block]'s [ChapterMeta] in `memo["meta"]`, keeping the other memo keys, and marks a locked chapter's [name]
 * with `🔒` unless the app shows `locked` itself. Call it after setting [SChapter.name].
 */
fun SChapter.setMeta(block: ChapterMeta.() -> Unit) = setMeta(shownMetaKeys(), block)

internal fun SChapter.setMeta(shown: Set<String>, block: ChapterMeta.() -> Unit) {
    val meta = ChapterMeta().apply(block)
    if (meta.locked && "locked" !in shown && !name.startsWith(LOCK)) name = LOCK + name
    memo = memo.withMeta(meta.toJson())
}

/** Keeps the outer builder's functions out of [MangaDescription.meta]. */
@DslMarker
annotation class DescriptionDsl

/** Builds a manga description for [SManga.describe]; see there. */
@DescriptionDsl
class MangaDescription internal constructor(private val shown: Set<String>) {
    private val sections = mutableListOf<String>()
    private var lastIsInline = false
    private var lastIsRating = false
    internal val facts = MangaMeta()
    private val blocksShown get() = "sections" in shown

    private fun add(section: String?) {
        if (section.isNullOrBlank()) return
        sections += section
        endRun()
    }

    private fun addInline(text: String?) {
        if (text.isNullOrBlank()) return
        when {
            lastIsRating -> sections[sections.lastIndex] += "\n$text"
            lastIsInline -> sections[sections.lastIndex] += " · $text"
            else -> sections += text
        }
        lastIsInline = true
        lastIsRating = false
    }

    private fun endRun() {
        lastIsInline = false
        lastIsRating = false
    }

    /** Adds [rating], out of [max], as stars followed by the number and the [votes] count, on its own line. */
    fun rating(rating: Number?, max: Int = 10, votes: Number? = null) {
        val value = facts.addRating(rating, max, votes) ?: return
        if ("rating" in shown) return endRun()
        val number = BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        add("${ratingStars(value, max)} $number" + (votes?.let { count -> " ($count)" } ?: ""))
        lastIsRating = true
    }

    /** Adds [facts], separated by dots. They have no block, so prefer [field] for labelled facts. */
    fun info(vararg facts: String?) = addInline(facts.filterNot { it.isNullOrBlank() }.joinToString(" · ") { it!!.trim().escapeMarkdown() })

    /** Adds plain [text]. */
    fun description(text: String?) = add(text?.trim()?.escapeMarkdown())

    /** Adds the content of [html] with [toMarkdown]. */
    fun description(html: Element?, keepNewlines: Boolean = false) = add(html?.toMarkdown(keepNewlines))

    /** Adds [markdown] as it is, for text that is Markdown already. */
    fun markdown(markdown: String?) = add(markdown?.trim())

    /** Adds [value] after a bold [label] (a trailing colon is dropped); see [MangaMeta.field] for [summary]. */
    fun field(label: String, value: String?, summary: Boolean = false) {
        val (name, text) = facts.addField(label, value, summary) ?: return
        if (blocksShown) return
        addInline("**${name.escapeMarkdown()}:** ${text.escapeMarkdown()}")
    }

    /** Adds [values], separated by commas, like [field]. */
    fun field(label: String, values: Collection<String>, summary: Boolean = false) = field(label, values.filterNot { it.isBlank() }.joinToString { it.trim() }, summary)

    /** Adds [titles] as a list under [label] (a trailing colon is dropped), without blanks and repeats. */
    fun altTitles(titles: Collection<String>, label: String = "Alternative Names") {
        val (name, kept) = facts.addTitles(titles, label) ?: return
        facts.altTitles += kept
        if (blocksShown) return endRun()
        add(markdownList(name, kept.map { it.escapeMarkdown() }))
    }

    /** Adds [links], pairs of link text and URL, as a list under [label]. */
    fun links(links: Collection<Pair<String, String?>>, label: String = "Links") {
        val kept = facts.addLinks(links, label) ?: return
        if (blocksShown) return endRun()
        add(markdownList(label, kept.map { (text, url) -> "[${text.escapeMarkdown()}](${url.encodeMarkdownUrl()})" }))
    }

    /** Adds tracker entries, [MangaMeta.Tracker] to the site's id, as a list of links under "Trackers". */
    fun trackers(vararg ids: Pair<MangaMeta.Tracker, Any?>) = printTrackers(facts.addTrackers(ids.toList()))

    /** Adds the tracker pages among [urls] like [trackers]; other urls are left out, so give them to [links]. */
    fun trackers(urls: Collection<String?>) = printTrackers(facts.addTrackers(urls.mapNotNull { MangaMeta.Tracker.fromUrl(it) }))

    private fun printTrackers(kept: Map<MangaMeta.Tracker, String>) {
        if (kept.isEmpty()) return
        if ("trackers" in shown) return endRun()
        add(markdownList("Trackers", kept.map { (tracker, id) -> "[${tracker.title}](${tracker.url(id).encodeMarkdownUrl()})" }))
    }

    /** Adds a horizontal rule (`---`). */
    fun separator() = add(SEPARATOR)

    /** Sets typed facts and adds blocks the description doesn't show. */
    fun meta(block: MangaMeta.() -> Unit) = facts.block()

    internal fun build(): String? {
        val kept = sections.fold(mutableListOf<String>()) { kept, section ->
            if (section != SEPARATOR || (kept.isNotEmpty() && kept.last() != SEPARATOR)) kept += section
            kept
        }
        if (kept.lastOrNull() == SEPARATOR) kept.removeAt(kept.lastIndex)
        return kept.joinToString("\n\n").ifEmpty { null }
    }

    private companion object {
        const val SEPARATOR = "---"
    }
}

/**
 * Structured details of a manga, stored in `memo["meta"]` and specified by the extensions-lib schema (schema/memo-meta.schema.json): a few typed
 * facts for searching, sorting and filtering, and display blocks (`sections`) in the order they're added. The block
 * functions here add nothing to the description.
 */
@DescriptionDsl
class MangaMeta internal constructor() {
    /** Other titles of the series, for searching. */
    var altTitles: List<String> = emptyList()

    /** The site's rating; [rating] sets it. */
    var rating: Rating? = null
        private set
    var contentRating: ContentRating? = null

    /** The site's genres or main categories, when [SManga.genre] also holds other tags. */
    var primaryTags: List<String> = emptyList()

    /** The series' ids on trackers, for apps to link them; [trackers] adds to it. */
    val trackers: Map<Tracker, String> get() = trackerIds

    private val trackerIds = linkedMapOf<Tracker, String>()

    private val blocks = mutableListOf<Block>()

    /**
     * Adds a [label]/[value] row, under the rows right before it. A [summary] row is a short fact (type, year,
     * language) fit for a one-line overview.
     */
    fun field(label: String, value: String?, summary: Boolean = false) {
        addField(label, value, summary)
    }

    internal fun addField(label: String, value: String?, summary: Boolean): Pair<String, String>? {
        val text = value.nonBlank() ?: return null
        val name = label.trim().removeSuffix(":").trim()
        val row = Row(name, text, summary)
        val last = blocks.lastOrNull() as? Block.Fields
        if (last != null) last.rows += row else blocks += Block.Fields(mutableListOf(row))
        return name to text
    }

    /** Adds [values], separated by commas, like [field]. */
    fun field(label: String, values: Collection<String>, summary: Boolean = false) = field(label, values.mapNotNull { it.nonBlank() }.joinToString(), summary)

    /** Sets [rating] to [value] out of [max], from [votes] votes. */
    fun rating(value: Number?, max: Int = 10, votes: Number? = null) {
        addRating(value, max, votes)
    }

    internal fun addRating(value: Number?, max: Int, votes: Number?): Double? {
        // Through the decimal text, so a Float such as 9.1f stays 9.1
        val kept = value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && max > 0 } ?: return null
        rating = Rating(kept, max, votes?.toLong()?.takeIf { it >= 0 })
        return kept
    }

    /** A rating of [value] out of [max], from [votes] votes when the site gives a count. */
    class Rating internal constructor(val value: Double, val max: Int, val votes: Long?)

    /** Adds [titles] as a list under [label]. */
    fun titles(titles: Collection<String>, label: String) {
        addTitles(titles, label)
    }

    internal fun addTitles(titles: Collection<String>, label: String): Pair<String, List<String>>? {
        val kept = titles.mapNotNull { it.nonBlank() }.distinct().ifEmpty { return null }
        val name = label.trim().removeSuffix(":").trim()
        blocks += Block.Titles(name, kept)
        return name to kept
    }

    /** Adds [links], pairs of link text and URL, under [label]. */
    fun links(links: Collection<Pair<String, String?>>, label: String = "Links") {
        addLinks(links, label)
    }

    internal fun addLinks(links: Collection<Pair<String, String?>>, label: String): List<Pair<String, String>>? {
        val kept = links.mapNotNull { (text, url) ->
            val href = url?.trim()?.toHttpUrlOrNull()?.toString() ?: return@mapNotNull null
            text.nonBlank()?.let { it to href }
        }.ifEmpty { return null }
        blocks += Block.Links(label.trim(), kept)
        return kept
    }

    /**
     * Adds [values] as chips under [label]. [search]: each chip is a search term for the source. [collapsed]: shown
     * folded, e.g. tags the site tucks away. [spoiler]: hidden until the reader asks, e.g. the site's spoiler tags.
     */
    fun chips(label: String, values: Collection<String>, search: Boolean = true, collapsed: Boolean = false, spoiler: Boolean = false) {
        val kept = values.mapNotNull { it.nonBlank() }.distinct().ifEmpty { return }
        blocks += Block.Chips(label.trim(), kept, search, collapsed, spoiler)
    }

    /** Adds [markdown] as a paragraph. */
    fun text(markdown: String?) {
        blocks += Block.Text(markdown.nonBlank() ?: return)
    }

    /** Adds tracker entries, [Tracker] to the site's id. */
    fun trackers(vararg ids: Pair<Tracker, Any?>) {
        addTrackers(ids.toList())
    }

    /** Adds the tracker pages among [urls]. */
    fun trackers(urls: Collection<String?>) {
        addTrackers(urls.mapNotNull { Tracker.fromUrl(it) })
    }

    internal fun addTrackers(ids: Collection<Pair<Tracker, Any?>>): Map<Tracker, String> {
        val kept = linkedMapOf<Tracker, String>()
        ids.forEach { (tracker, id) -> id?.toString().nonBlank()?.let { if (tracker !in trackerIds) kept[tracker] = it } }
        trackerIds += kept
        return kept
    }

    enum class ContentRating { SAFE, SUGGESTIVE, ADULT }

    /** A tracker, with the key it's stored under and its series page. */
    enum class Tracker(internal val key: String, internal val title: String, private val page: String) {
        ANILIST("anilist", "AniList", "https://anilist.co/manga/"),
        MYANIMELIST("myanimelist", "MyAnimeList", "https://myanimelist.net/manga/"),
        KITSU("kitsu", "Kitsu", "https://kitsu.app/manga/"),
        MANGAUPDATES("mangaupdates", "MangaUpdates", "https://www.mangaupdates.com/series/"),
        ANIMEPLANET("animeplanet", "Anime-Planet", "https://www.anime-planet.com/manga/"),
        MANGABAKA("mangabaka", "MangaBaka", "https://mangabaka.org/"),
        SHIKIMORI("shikimori", "Shikimori", "https://shikimori.io/mangas/"),
        NOVELUPDATES("novelupdates", "NovelUpdates", "https://www.novelupdates.com/series/"),
        ;

        // MangaUpdates' old numeric ids only open on the old page
        internal fun url(id: String) = if (this == MANGAUPDATES && id.all(Char::isDigit)) "https://www.mangaupdates.com/series.html?id=$id" else page + id

        companion object {
            /** The tracker and id of a tracker's series page, or null for any other url. */
            fun fromUrl(url: String?): Pair<Tracker, String>? {
                val parsed = url?.trim()?.toHttpUrlOrNull() ?: return null
                val host = parsed.host.removePrefix("www.")
                val segments = parsed.pathSegments.filter(String::isNotEmpty)
                fun at(index: Int) = segments.getOrNull(index)?.nonBlank()
                return when {
                    host == "anilist.co" && at(0) == "manga" -> at(1)?.let { ANILIST to it }
                    host == "myanimelist.net" && at(0) == "manga" -> at(1)?.let { MYANIMELIST to it }
                    (host == "kitsu.app" || host == "kitsu.io") && at(0) == "manga" -> at(1)?.let { KITSU to it }
                    host == "mangaupdates.com" && at(0) == "series" -> at(1)?.let { MANGAUPDATES to it }
                    host == "mangaupdates.com" && at(0) == "series.html" -> parsed.queryParameter("id")?.let { MANGAUPDATES to it }
                    host == "anime-planet.com" && at(0) == "manga" -> at(1)?.let { ANIMEPLANET to it }
                    host == "mangabaka.org" || host == "mangabaka.dev" -> at(0)?.let { MANGABAKA to it }
                    host.startsWith("shikimori.") && at(0) == "mangas" -> at(1)?.let { SHIKIMORI to it }
                    host == "novelupdates.com" && at(0) == "series" -> at(1)?.let { NOVELUPDATES to it }
                    else -> null
                }
            }
        }
    }

    private class Row(val label: String, val value: String, val summary: Boolean)

    private sealed class Block {
        class Fields(val rows: MutableList<Row>) : Block()
        class Titles(val label: String, val values: List<String>) : Block()
        class Links(val label: String, val links: List<Pair<String, String>>) : Block()
        class Chips(val label: String, val values: List<String>, val search: Boolean, val collapsed: Boolean, val spoiler: Boolean) : Block()
        class Text(val markdown: String) : Block()
    }

    internal fun toJson(title: String? = null) = metaJson {
        val notTitle = { text: String -> text.trim() != title?.trim() }
        putStrings("altTitles", altTitles.filter(notTitle))
        rating?.let { rating ->
            putJsonObject("rating") {
                put("value", rating.value)
                put("max", rating.max)
                rating.votes?.let { put("votes", it) }
            }
        }
        contentRating?.let { put("contentRating", it.name.lowercase()) }
        putStrings("primaryTags", primaryTags)
        if (trackerIds.isNotEmpty()) putJsonObject("trackers") { trackerIds.forEach { (tracker, id) -> put(tracker.key, id) } }
        if (blocks.isEmpty()) return@metaJson
        putJsonArray("sections") {
            blocks.forEach { block ->
                addJsonObject {
                    when (block) {
                        is Block.Fields -> {
                            put("type", "fields")
                            putJsonArray("fields") {
                                block.rows.forEach { row ->
                                    addJsonObject {
                                        put("label", row.label)
                                        put("value", row.value)
                                        if (row.summary) put("summary", true)
                                    }
                                }
                            }
                        }
                        is Block.Titles -> {
                            put("type", "titles")
                            put("label", block.label)
                            putJsonArray("values") { block.values.filter(notTitle).forEach { add(it) } }
                        }
                        is Block.Links -> {
                            put("type", "links")
                            put("label", block.label)
                            putJsonArray("links") {
                                block.links.forEach { (label, url) ->
                                    addJsonObject {
                                        put("label", label)
                                        put("url", url)
                                    }
                                }
                            }
                        }
                        is Block.Chips -> {
                            put("type", "chips")
                            put("label", block.label)
                            putJsonArray("values") { block.values.forEach { add(it) } }
                            if (block.search) put("search", true)
                            if (block.collapsed) put("collapsed", true)
                            if (block.spoiler) put("spoiler", true)
                        }
                        is Block.Text -> {
                            put("type", "text")
                            put("markdown", block.markdown)
                        }
                    }
                }
            }
        }
    }
}

/** Structured details of a chapter, stored in `memo["meta"]`; specified by the extensions-lib schema (schema/memo-meta.schema.json, `$defs/chapter`). */
@DescriptionDsl
class ChapterMeta internal constructor() {
    /** The site serves the chapter's pages only after a purchase, a login or a wait. */
    var locked: Boolean = false
    var volume: String? = null

    /** Every group credited, when [SChapter.scanlator] joins several. */
    var scanlators: List<String> = emptyList()

    /** BCP 47 tag of the chapter's language, when the source mixes languages. */
    var language: String? = null

    internal fun toJson() = metaJson {
        if (locked) put("locked", true)
        volume.nonBlank()?.let { put("volume", it) }
        putStrings("scanlators", scanlators)
        language.languageTag()?.let { put("language", it) }
    }
}

private const val LOCK = "🔒 "

// The title, when the source set it before describe
private fun SManga.titleOrNull(): String? = try {
    title
} catch (_: UninitializedPropertyAccessException) {
    null
}

// Apps without `AppInfo.getShownMetaKeys` show none.
private val appHasShownMetaKeys by lazy {
    try {
        AppInfo.getShownMetaKeys()
        true
    } catch (_: LinkageError) {
        false
    }
}

private fun shownMetaKeys(): Set<String> = if (appHasShownMetaKeys) AppInfo.getShownMetaKeys() else emptySet()

internal fun SManga.keepMetaOf(stored: SManga) {
    if (this !== stored && MEMO_META !in memo) memo = memo.withMeta(stored.memo[MEMO_META] as? JsonObject)
}

private fun JsonObject.withMeta(meta: JsonObject?): JsonObject = when {
    meta != null -> JsonObject(this + (MEMO_META to meta))
    MEMO_META in this -> JsonObject(this - MEMO_META)
    else -> this
}

private inline fun metaJson(fill: JsonObjectBuilder.() -> Unit): JsonObject? = buildJsonObject {
    put("v", META_VERSION)
    fill()
}.takeIf { it.size > 1 }

private fun JsonObjectBuilder.putStrings(key: String, values: List<String>) {
    val kept = values.mapNotNull { it.nonBlank() }.distinct()
    if (kept.isNotEmpty()) putJsonArray(key) { kept.forEach { add(it) } }
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private val LANGUAGE_TAG = Regex("""^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$""")

private fun String?.languageTag(): String? = nonBlank()?.takeIf(LANGUAGE_TAG::matches)
