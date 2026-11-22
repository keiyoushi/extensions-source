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
 * Sets [SManga.description], as Markdown, and stores the rating, alternative titles, tracker ids and links it adds
 * in `memo["meta"]`, keeping the other memo keys. [MangaDescription.meta] stores facts the description leaves out,
 * such as the content rating. A key the app lists in `AppInfo.getShownMetaKeys()` (`rating`, `altTitles`,
 * `trackers`, `links`) is left out of the description.
 *
 * Sections come in call order, separated by a blank line, except a run of [MangaDescription.info] and
 * [MangaDescription.field], which share one line. Site text is escaped; null, blank and empty input adds nothing.
 *
 * ```
 * describe {
 *     rating(score, votes = votes)
 *     field("Type", type)
 *     separator()
 *     description(document.selectFirst(".synopsis")) // or plain text
 *     field("Publisher", publisher)
 *     altTitles(altTitles)
 *     trackers(MangaMeta.Tracker.ANILIST to anilistId)
 *     links(listOf("Official" to officialUrl))
 *     meta { contentRating = MangaMeta.ContentRating.SAFE }
 * }
 * ```
 */
fun SManga.describe(block: MangaDescription.() -> Unit) = describe(shownMetaKeys(), block)

internal fun SManga.describe(shown: Set<String>, block: MangaDescription.() -> Unit) {
    val title = titleOrNull()
    val builder = MangaDescription(shown, title).apply(block)
    description = builder.build()
    memo = memo.withMeta(builder.facts.toJson(title))
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
class MangaDescription internal constructor(private val shown: Set<String>, private val title: String? = null) {
    private val sections = mutableListOf<String>()
    private var lastIsInline = false
    private var lastIsRating = false
    internal val facts = MangaMeta()

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

    /** Adds [facts], separated by dots. */
    fun info(vararg facts: String?) = addInline(facts.filterNot { it.isNullOrBlank() }.joinToString(" · ") { it!!.trim().escapeMarkdown() })

    /** Adds plain [text]. */
    fun description(text: String?) = add(text?.trim()?.escapeMarkdown())

    /** Adds the content of [html] with [toMarkdown]. */
    fun description(html: Element?, keepNewlines: Boolean = false) = add(html?.toMarkdown(keepNewlines))

    /** Adds [markdown] as it is, for text that is Markdown already. */
    fun markdown(markdown: String?) = add(markdown?.trim())

    /** Adds [value] after a bold [label] (a trailing colon is dropped). */
    fun field(label: String, value: String?) {
        val text = value.nonBlank() ?: return
        addInline("**${label.trim().removeSuffix(":").trim().escapeMarkdown()}:** ${text.escapeMarkdown()}")
    }

    /** Adds [values], separated by commas, like [field]. */
    fun field(label: String, values: Collection<String>) = field(label, values.filterNot { it.isBlank() }.joinToString { it.trim() })

    /** Adds [titles] as a list under [label] (a trailing colon is dropped), without blanks, repeats and the title. */
    fun altTitles(titles: Collection<String>, label: String = "Alternative Names") {
        val kept = titles.mapNotNull { it.nonBlank() }.distinct().filter { it != title?.trim() }.ifEmpty { return }
        facts.altTitles += kept
        if ("altTitles" in shown) return endRun()
        add(markdownList(label.trim().removeSuffix(":").trim(), kept.map { it.escapeMarkdown() }))
    }

    /** Adds [links], pairs of link text and URL, as a list under [label]; [language]: of the edition they link to. */
    fun links(links: Collection<Pair<String, String?>>, label: String = "Links", language: String? = null) {
        val kept = facts.addLinks(links, language).ifEmpty { return }
        if ("links" in shown) return endRun()
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

    /** Stores facts without adding them to the description. */
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
 * Structured details of a manga, stored in `memo["meta"]` and specified by the extensions-lib schema
 * (schema/memo-meta.schema.json). Nothing set here goes into the description.
 */
@DescriptionDsl
class MangaMeta internal constructor() {
    /** Other titles of the series, in any language; the title itself is left out. */
    var altTitles: List<String> = emptyList()

    /** Audience the site rates the series for. */
    var contentRating: ContentRating? = null

    private var rating: Rating? = null
    private val trackerIds = linkedMapOf<Tracker, String>()
    private val links = mutableListOf<Link>()

    /** Sets the site's rating to [value] out of [max], from [votes] votes. */
    fun rating(value: Number?, max: Int = 10, votes: Number? = null) {
        addRating(value, max, votes)
    }

    internal fun addRating(value: Number?, max: Int, votes: Number?): Double? {
        // Through the decimal text, so a Float such as 9.1f stays 9.1
        val kept = value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && max > 0 } ?: return null
        rating = Rating(kept, max, votes?.toLong()?.takeIf { it >= 0 })
        return kept
    }

    /** Adds [links], pairs of link text and URL; [language]: BCP 47 tag of the edition they link to. */
    fun links(links: Collection<Pair<String, String?>>, language: String? = null) {
        addLinks(links, language)
    }

    internal fun addLinks(links: Collection<Pair<String, String?>>, language: String?): List<Pair<String, String>> {
        val tag = language.languageTag()
        val kept = links.mapNotNull { (text, url) ->
            val href = url?.trim()?.toHttpUrlOrNull()?.toString() ?: return@mapNotNull null
            text.nonBlank()?.let { it to href }
        }
        kept.forEach { (label, url) -> if (this.links.none { it.url == url }) this.links += Link(label, url, tag) }
        return kept
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
        ids.forEach { (tracker, id) -> id?.toString().nonBlank()?.let { if (tracker !in trackerIds && tracker !in kept) kept[tracker] = it } }
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
                    // The page's "z116778-chainsaw-man" and "116778" are the API's id 116778
                    host.startsWith("shikimori.") && at(0) == "mangas" -> at(1)?.let(SHIKIMORI_ID::find)?.let { SHIKIMORI to it.groupValues[1] }
                    host == "novelupdates.com" && at(0) == "series" -> at(1)?.let { NOVELUPDATES to it }
                    else -> null
                }
            }
        }
    }

    private class Rating(val value: Double, val max: Int, val votes: Long?)

    private class Link(val label: String, val url: String, val language: String?)

    internal fun toJson(title: String? = null) = metaJson {
        rating?.let { rating ->
            putJsonObject("rating") {
                put("value", rating.value)
                put("max", rating.max)
                rating.votes?.let { put("votes", it) }
            }
        }
        contentRating?.let { put("contentRating", it.name.lowercase()) }
        putStrings("altTitles", altTitles.filter { it.trim() != title?.trim() })
        if (trackerIds.isNotEmpty()) putJsonObject("trackers") { trackerIds.forEach { (tracker, id) -> put(tracker.key, id) } }
        if (links.isEmpty()) return@metaJson
        putJsonArray("links") {
            links.forEach { link ->
                addJsonObject {
                    put("label", link.label)
                    put("url", link.url)
                    link.language?.let { put("language", it) }
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

    internal fun toJson() = metaJson {
        if (locked) put("locked", true)
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

private val SHIKIMORI_ID = Regex("""^[a-z]?(\d+)(?:-|$)""")

private val LANGUAGE_TAG = Regex("""^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$""")

private fun String?.languageTag(): String? = nonBlank()?.takeIf(LANGUAGE_TAG::matches)
