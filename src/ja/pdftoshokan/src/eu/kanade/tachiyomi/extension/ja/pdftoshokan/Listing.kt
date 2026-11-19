package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.decodeHex
import keiyoushi.utils.xor
import org.jsoup.nodes.Document

/**
 * The titles of a listing page. Their links are XOR-encoded with a key from the page's scripts, and the page hides
 * honeypots among them: following one gets the client IP banned.
 */
internal fun Document.parseListing(): List<SManga> {
    val scripts = select("script:not([src])").joinToString("\n") { it.data() }
    val (linkKey, linkAttr) = HES_CONFIG_REGEX.find(scripts)!!.destructured

    // A honeypot is a collapsed article, or one the scripts single out. Its link only ever gets read here, from the
    // raw HTML: the page hooks getAttribute on it to navigate there.
    val styles = select("style").joinToString("\n") { it.data() }
    val hiddenClasses = HIDDEN_CLASS_REGEX.findAll(styles).map { it.groupValues[1] }.toSet() +
        TRAP_CLASS_REGEX.findAll(scripts).map { it.groupValues[1] }
    val trapIds = TRAP_REGEX.findAll(scripts).map { it.groupValues[1] }.toSet() + TRAP_ID

    return select("article").mapNotNull { article ->
        if (article.classNames().any { it in hiddenClasses }) return@mapNotNull null
        val link = article.selectFirst(".entry-title a[$linkAttr]") ?: return@mapNotNull null
        val target = link.attr(linkAttr).decodeHex().xor(linkKey.encodeToByteArray()).decodeToString()
        val id = MANGA_ID_REGEX.find(target)?.groupValues?.get(1) ?: return@mapNotNull null
        if (id in trapIds) return@mapNotNull null

        SManga.create().apply {
            url = id
            title = link.text()
            thumbnail_url = article.selectFirst(".entry-content img")?.absUrl("src")
        }
    }
}

/** Whether the listing page links to the page after [page]. Some listings drop the slash before `page/`. */
internal fun Document.hasPageAfter(page: Int): Boolean = select("a[href*=page/${page + 1}]")
    .any { it.attr("href").substringAfterLast("page/").trimEnd('/') == "${page + 1}" }

internal fun Document.parseDetails(id: String) = SManga.create().apply {
    url = id
    title = selectFirst("h1.entry-title")!!.text()
    thumbnail_url = selectFirst(".entry-content img")?.absUrl("src")
        ?: selectFirst("meta[property=og:image]")?.attr("content")
    description = select(".entry-content > p")
        .filter { it.selectFirst("img, select, input") == null }
        .joinToString("\n\n") { it.text() }
        .ifBlank { null }
    genre = select(".cat-links a").joinToString { it.text() }.ifBlank { null }
    // Tags hold the author names; their href is a honeypot, so only the text is used
    author = select(".tags-links a").joinToString { it.text() }.ifBlank { null }
    status = SManga.UNKNOWN
}

/** The volumes of a title page, as its read button's selector names them. */
internal fun Document.volumeLabels(): List<String> = select("select.vi13 option").map { it.text() }.distinct()

/** The title id in a site path, unless it's the honeypot's. */
internal fun mangaIdOf(path: String): String? = MANGA_ID_REGEX.find(path)?.groupValues?.get(1)?.takeIf { it != TRAP_ID }

private const val TRAP_ID = "4973674"

private val HES_CONFIG_REGEX = Regex("""key\s*:\s*"([^"]+)"\s*,\s*randAttr\s*:\s*"([^"]+)"""")
private val HIDDEN_CLASS_REGEX = Regex("""\.(category-\d+)\s*\{[^}]*?(?:display\s*:\s*none|opacity\s*:\s*0|(?<![-\w])width\s*:\s*0)""")
private val TRAP_CLASS_REGEX = Regex("""querySelector\(\s*["']\.(category-\d+)\s+a["']""")
private val TRAP_REGEX = Regex("""location\.href\s*=\s*['"]/manga/(\d+)""")
private val MANGA_ID_REGEX = Regex("""/manga/(\d+)""")
