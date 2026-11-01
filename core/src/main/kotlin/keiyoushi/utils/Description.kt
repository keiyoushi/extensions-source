package keiyoushi.utils

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
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
 * Formats [items] as a bullet list under a bold [label], or null when there are none.
 *
 * @param escape set to false when the items are already Markdown, such as links
 */
fun markdownList(label: String, items: Collection<String>, escape: Boolean = true): String? {
    if (items.isEmpty()) return null
    return items.joinToString("\n", prefix = "**$label:**\n") { "- ${if (escape) it.escapeMarkdown() else it}" }
}

/** Formats a link to [url], escaping [text]. */
fun markdownLink(text: String, url: String): String = "[${text.escapeMarkdown()}](${url.encodeMarkdownUrl()})"

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
