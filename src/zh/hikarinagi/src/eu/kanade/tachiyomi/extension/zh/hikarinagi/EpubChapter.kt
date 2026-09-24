package eu.kanade.tachiyomi.extension.zh.hikarinagi

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.InputStream
import java.util.zip.ZipInputStream

class EpubChapter(
    val title: String,
    val blocks: List<EpubBlock>,
)

sealed interface EpubBlock {
    class Text(val text: String) : EpubBlock

    /** An illustration of the archive, kept as raw bytes until the page list is written. */
    class Image(val path: String, val bytes: ByteArray) : EpubBlock
}

/**
 * Elements that never break a paragraph apart. Everything else is treated as a container
 * whose children are walked, so wrappers like `<section>` or `<div>` do not collapse a
 * chapter into a single block.
 */
private val INLINE_TAGS = setOf(
    "a", "abbr", "b", "big", "br", "cite", "code", "del", "em", "font", "i", "img", "ins", "label",
    "mark", "q", "rp", "rt", "ruby", "s", "small", "span", "strike", "strong", "sub", "sup", "time", "u", "wbr",
)

private val IMAGE_TAGS = setOf("img", "image")

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")

/**
 * Reads the documents of an EPUB in spine order, keeping their text blocks and illustrations.
 *
 * Package documents, (X)HTML documents and images are buffered; fonts and other resources are
 * skipped. Documents without any content (empty pages) are dropped.
 */
fun readEpubChapters(input: InputStream): List<EpubChapter> {
    val documents = mutableMapOf<String, String>()
    val images = mutableMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = entry.name
            when {
                name.endsWith(".opf") || name.endsWith(".xhtml") || name.endsWith(".html") ->
                    documents[name] = zip.readBytes().toString(Charsets.UTF_8)

                name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS -> images[name] = zip.readBytes()
            }
        }
    }

    val packagePath = documents.keys.firstOrNull { it.endsWith(".opf") } ?: return emptyList()
    val packageDoc = Jsoup.parse(documents.getValue(packagePath), "", Parser.xmlParser())
    val baseDir = packagePath.substringBeforeLast('/', "")
    val manifest = packageDoc.select("manifest > item").associate { it.attr("id") to it.attr("href") }

    return packageDoc.select("spine > itemref").mapNotNull { itemref ->
        val href = manifest[itemref.attr("idref")] ?: return@mapNotNull null
        val documentPath = resolveEpubPath(baseDir, href)
        val html = documents[documentPath] ?: return@mapNotNull null

        val doc = Jsoup.parse(html)
        val heading = doc.selectFirst("body h1, body h2, body h3, body h4")
        val title = heading?.text()?.takeIf { it.isNotBlank() } ?: doc.title()
        // The heading is rendered separately as the page header.
        heading?.remove()

        val blocks = mutableListOf<EpubBlock>()
        doc.body().collectBlocks(documentPath.substringBeforeLast('/', ""), images, blocks)
        if (blocks.isEmpty()) null else EpubChapter(title, blocks)
    }
}

private fun Element.collectBlocks(base: String, images: Map<String, ByteArray>, out: MutableList<EpubBlock>) {
    val containers = children().filter { it.tagName() !in INLINE_TAGS }
    if (containers.isEmpty()) {
        text().takeIf { it.isNotBlank() }?.let { out.add(EpubBlock.Text(it)) }
        select(IMAGE_TAGS.joinToString()).mapNotNullTo(out) { it.toImageBlock(base, images) }
        return
    }

    ownText().takeIf { it.isNotBlank() }?.let { out.add(EpubBlock.Text(it)) }
    children().filter { it.tagName() in INLINE_TAGS }
        .mapNotNullTo(out) { it.toImageBlock(base, images) }
    containers.forEach { it.collectBlocks(base, images, out) }
}

private fun Element.toImageBlock(base: String, images: Map<String, ByteArray>): EpubBlock.Image? {
    // Superscript images are footnote markers, not illustrations.
    if (parents().any { it.tagName() == "sup" }) return null

    val source = attr("src").ifBlank { attr("xlink:href") }.ifBlank { attr("href") }
    val path = resolveEpubPath(base, source.substringBefore('#'))
    val bytes = images[path] ?: return null

    return EpubBlock.Image(path, bytes)
}

private fun resolveEpubPath(baseDir: String, href: String): String {
    val parts = baseDir.split('/').filter(String::isNotEmpty).toMutableList()
    href.removePrefix("/").split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> parts.removeLastOrNull()
            else -> parts.add(segment)
        }
    }
    return parts.joinToString("/")
}
