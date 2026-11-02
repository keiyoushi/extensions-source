package keiyoushi.utils

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

private val MARKDOWN_INDENT = Regex("""^[ \t]+""", RegexOption.MULTILINE)

private val MARKDOWN_INLINE = Regex("""[\\*_\[\]<]""")

// The renderer drops an escaped backtick, so only one that could pair up is escaped.
private val MARKDOWN_BACKTICK = Regex("""`(?=[^`]*`)""")

// Line starts: quotes, headings, list markers, fences, setext underlines and table delimiters, then list numbers.
private val MARKDOWN_BLOCK = Regex(
    """^(?:>|#{1,6}(?=\s|$)|[-+](?=\s|$)|~{3}|[-=:|~][-=:|~ \t]*$)|(?<=^\d{1,9})[.)](?=\s|$)""",
    RegexOption.MULTILINE,
)
private val MARKDOWN_LINE_END_SPACE = Regex("""[ \t]+$""", RegexOption.MULTILINE)
private val MARKDOWN_LINE_START_SPACE = Regex("""^[ \t]++(?!- |\d+\. )""", RegexOption.MULTILINE)
private val MARKDOWN_INNER_SPACE = Regex("""(?<=\S)[ \t]{2,}""")
private val MARKDOWN_LINE_BREAKS = Regex("""\n+""")
private val MARKDOWN_BLANK_LINES = Regex("""\n{3,}""")

/**
 * Escapes this plain text so that a description doesn't format it as Markdown. Indentation is
 * removed since it would start a code block.
 *
 * Useful for site text that isn't meant as Markdown. Escape each piece before adding Markdown of
 * your own.
 */
fun String.escapeMarkdown(): String = escapeMarkdownSyntax().escapeMarkdownBackticks()

private fun String.escapeMarkdownSyntax() = replace(MARKDOWN_INDENT, "")
    .replace(MARKDOWN_INLINE) { "\\${it.value}" }
    .replace(MARKDOWN_BLOCK) { "\\${it.value}" }

private fun String.escapeMarkdownBackticks() = replace(MARKDOWN_BACKTICK) { "\\${it.value}" }

/**
 * Builds a Markdown description from sections, in the order they're added. Sections are separated by a blank line,
 * except a run of [rating], [info] and [field], which share one line. Null, blank and empty input adds nothing.
 *
 * ```
 * description = MarkdownDescription.Builder()
 *     .rating(score, votes = votes)
 *     .info(year, type)
 *     .description(document.selectFirst(".synopsis")) // or plain text
 *     .field("Publisher", publisher)
 *     .altTitles(altTitles)
 *     .links(listOf("AniList" to anilistUrl, "MyAnimeList" to malUrl))
 *     .build()
 * ```
 *
 * Pass the source's preferences to leave out what the user hid with [addPreference].
 */
object MarkdownDescription {
    private const val PREF_HIDDEN_SECTIONS = "description_hidden_sections"

    // Sections a user can hide; the description itself is always shown.
    private enum class Section(val title: String) {
        RATING("Rating"),
        DETAILS("Details"),
        ALT_TITLES("Alternative names"),
        LINKS("Links"),
    }

    class Builder(preferences: SharedPreferences? = null) {
        private val hidden = preferences?.getStringSet(PREF_HIDDEN_SECTIONS, null).orEmpty()
        private val sections = mutableListOf<String>()
        private var lastIsInline = false

        private fun add(section: String?, kind: Section? = null) = apply {
            if (section.isNullOrBlank() || kind?.name in hidden) return@apply
            sections += section
            lastIsInline = false
        }

        private fun addInline(text: String?, kind: Section) = apply {
            if (text.isNullOrBlank() || kind.name in hidden) return@apply
            if (lastIsInline) sections[sections.lastIndex] += " · $text" else sections += text
            lastIsInline = true
        }

        /** Adds [rating], out of [max], as stars followed by the number and the [votes] count. */
        fun rating(rating: Number?, max: Int = 10, votes: Number? = null) = addInline(
            rating?.toDouble()?.takeIf { it.isFinite() }?.let { value ->
                val number = BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                "${ratingStars(value, max)} $number" + (votes?.let { count -> " ($count)" } ?: "")
            },
            Section.RATING,
        )

        /** Adds [facts], separated by dots. */
        fun info(vararg facts: String?) = addInline(facts.filterNot { it.isNullOrBlank() }.joinToString(" · ") { it!!.trim().escapeMarkdown() }, Section.DETAILS)

        /** Adds plain [text]. */
        fun description(text: String?) = add(text?.trim()?.escapeMarkdown())

        /** Adds the content of [html] with [toMarkdown]. */
        fun description(html: Element?, keepNewlines: Boolean = false) = add(html?.toMarkdown(keepNewlines))

        /** Adds [markdown] as it is, for text that is Markdown already. */
        fun markdown(markdown: String?) = add(markdown?.trim())

        /** Adds [value] after a bold [label]. */
        fun field(label: String, value: String?) = addInline(
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { "**${label.escapeMarkdown()}:** ${it.escapeMarkdown()}" },
            Section.DETAILS,
        )

        /** Adds [values], separated by commas, after a bold [label]. */
        fun field(label: String, values: Collection<String>) = field(label, values.filterNot { it.isBlank() }.joinToString { it.trim() })

        /** Adds [titles] as a list, without blanks and repeats. */
        fun altTitles(titles: Collection<String>, label: String = "Alternative Names") = add(
            markdownList(label, titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { it.escapeMarkdown() }),
            Section.ALT_TITLES,
        )

        /** Adds [links], pairs of link text and URL, as a list. */
        fun links(links: Collection<Pair<String, String?>>, label: String = "Links") = add(
            markdownList(
                label,
                links.mapNotNull { (text, url) -> url?.takeIf { it.isNotBlank() }?.let { "[${text.escapeMarkdown()}](${it.encodeMarkdownUrl()})" } },
            ),
            Section.LINKS,
        )

        /** Returns the description, or null when nothing was added. */
        fun build(): String? = sections.joinToString("\n\n").ifEmpty { null }
    }

    /** Adds a setting to hide the rating, details, alternative names or links from descriptions built with the source's preferences. */
    fun addPreference(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = PREF_HIDDEN_SECTIONS
            title = "Hide from description"
            summary = "Applies when an entry is refreshed"
            entries = Section.entries.map { it.title }.toTypedArray()
            entryValues = Section.entries.map { it.name }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }.also(screen::addPreference)
    }
}

private fun markdownList(label: String, items: List<String>): String? {
    val lines = items.filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    return lines.joinToString("\n", prefix = "**${label.escapeMarkdown()}:**\n") { "- $it" }
}

/** Formats [rating], out of [max], as five stars with half stars. */
fun ratingStars(rating: Double, max: Int = 10): String {
    val halves = ceil(rating * 10 / max - 0.5).toInt().coerceIn(0, 10)
    return "★".repeat(halves / 2) + "✬".repeat(halves % 2) + "☆".repeat(5 - (halves + 1) / 2)
}

private fun String.encodeMarkdownUrl() = replace(" ", "%20").replace("(", "%28").replace(")", "%29").replace("`", "%60")

/**
 * Converts this element's content to Markdown, escaping its text. Paragraphs, line breaks, bold, italics, links and
 * lists carry over, headings become bold and images are dropped. Edit the element first to change any of that or to
 * remove what doesn't belong in the description.
 *
 * @param keepNewlines keeps newlines in the text as line breaks, for sites that display them
 */
fun Element.toMarkdown(keepNewlines: Boolean = false): String = buildString { appendMarkdownChildren(this@toMarkdown, bold = false, keepNewlines) }
    .replace(MARKDOWN_LINE_END_SPACE, "")
    .replace(MARKDOWN_LINE_START_SPACE, "")
    .replace(MARKDOWN_INNER_SPACE, " ")
    .replace(MARKDOWN_BLANK_LINES, "\n\n")
    .trim()
    .escapeMarkdownBackticks()

private fun StringBuilder.appendMarkdownChildren(parent: Element, bold: Boolean, keepNewlines: Boolean) {
    parent.childNodes().forEach { appendMarkdown(it, bold, keepNewlines) }
}

private fun StringBuilder.appendMarkdown(node: Node, bold: Boolean, keepNewlines: Boolean) {
    when (node) {
        is TextNode -> if (keepNewlines) {
            node.wholeText.lines().forEachIndexed { index, line ->
                if (index > 0) append('\n')
                appendMarkdownText(TextNode(line).text())
            }
        } else {
            appendMarkdownText(node.text())
        }
        is Element -> when (val tag = node.normalName()) {
            "br" -> append('\n')
            "b", "strong" -> appendMarkdownEmphasis(node, if (bold) "" else "**", bold = true, keepNewlines)
            "i", "em" -> appendMarkdownEmphasis(node, "*", bold, keepNewlines)
            "a" -> appendMarkdownLink(node, bold, keepNewlines)
            "ul", "ol" -> appendMarkdownList(node, ordered = tag == "ol", bold, keepNewlines)
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                append("\n\n")
                appendMarkdownEmphasis(node, if (bold) "" else "**", bold = true, keepNewlines)
                append("\n\n")
            }
            else -> if (node.isBlock) {
                append("\n\n")
                appendMarkdownChildren(node, bold, keepNewlines)
                append("\n\n")
            } else {
                appendMarkdownChildren(node, bold, keepNewlines)
            }
        }
    }
}

// escapeMarkdown drops indentation, which between inline nodes is the space separating two words.
private fun StringBuilder.appendMarkdownText(text: String) {
    if (text.startsWith(' ')) append(' ')
    append(text.trim(' ').escapeMarkdownSyntax())
    if (text.length > 1 && text.endsWith(' ')) append(' ')
}

private fun StringBuilder.appendMarkdownEmphasis(element: Element, marker: String, bold: Boolean, keepNewlines: Boolean) {
    val content = buildString { appendMarkdownChildren(element, bold, keepNewlines) }
    val text = content.trim()
    if (text.isEmpty()) {
        append(content)
        return
    }
    append(content.takeWhile { it.isWhitespace() })
    val after = (element.nextSibling() as? TextNode)?.text()?.firstOrNull()
    // Markers between a letter and punctuation aren't emphasis.
    val opens = !(lastOrNull().isLetterOrDigit() && text.first().isPunctuation())
    val closes = !(after.isLetterOrDigit() && text.last().isPunctuation())
    if ('\n' !in text && opens && closes) append(marker, text, marker) else append(text)
    append(content.takeLastWhile { it.isWhitespace() })
}

private fun Char?.isLetterOrDigit() = this != null && Character.isLetterOrDigit(this)

private fun Char.isPunctuation() = !Character.isLetterOrDigit(this) && !isWhitespace()

private fun StringBuilder.appendMarkdownLink(link: Element, bold: Boolean, keepNewlines: Boolean) {
    val content = buildString { appendMarkdownChildren(link, bold, keepNewlines) }
    val text = content.trim()
    if (text.isEmpty()) {
        append(content)
        return
    }
    append(content.takeWhile { it.isWhitespace() })
    val url = link.absUrl("href")
    when {
        !url.startsWith("http") || '\n' in text -> append(text)
        else -> {
            // A link after "!" is an image.
            if (endsWith('!')) insert(length - 1, '\\')
            append('[', text, "](", url.encodeMarkdownUrl(), ')')
        }
    }
    append(content.takeLastWhile { it.isWhitespace() })
}

private fun StringBuilder.appendMarkdownList(list: Element, ordered: Boolean, bold: Boolean, keepNewlines: Boolean) {
    append("\n\n")
    list.childNodes().filter { (it as? Element)?.normalName() != "li" }.forEach {
        appendMarkdown(it, bold, keepNewlines)
        append("\n\n")
    }
    list.children().filter { it.normalName() == "li" }.forEachIndexed { index, item ->
        val marker = if (ordered) "${index + 1}. " else "- "
        val text = buildString { appendMarkdownChildren(item, bold, keepNewlines) }.trim()
            .replace(MARKDOWN_LINE_BREAKS, "\n" + " ".repeat(marker.length))
        if (text.isNotEmpty()) append(marker, text, '\n')
    }
    append('\n')
}
