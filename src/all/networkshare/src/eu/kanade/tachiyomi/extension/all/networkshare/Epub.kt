package eu.kanade.tachiyomi.extension.all.networkshare

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import keiyoushi.zip.Entry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl
import okio.buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

/** An image EPUB, read as the app's Local source reads it: the images of its spine's pages, in order. */
class Epub private constructor(
    private val share: Share,
    private val path: String,
    private val entries: Map<String, Entry>,
    private val packagePath: String,
    private val packageDocument: Document,
) {
    suspend fun images(): List<Entry> {
        val permits = Semaphore(PAGE_READS)
        return coroutineScope {
            pagePaths().map { async { permits.withPermit { pageImages(it) } } }.awaitAll().flatten()
        }
    }

    suspend fun firstImage(): Entry? = pagePaths().firstNotNullOfOrNull { pageImages(it).firstOrNull() }

    fun applyTo(manga: SManga) {
        meta("creator")?.let { manga.author = it }
        meta("description")?.let { manga.description = MarkdownDescription.Builder().description(it).build() }
    }

    fun applyTo(chapter: SChapter) {
        meta("title")?.let { chapter.name = it }
        (meta("publisher") ?: meta("creator"))?.let { chapter.scanlator = it }
        val date = meta("date") ?: packageDocument.selectFirst("*|meta[property=dcterms:modified]")?.textOrNull()
        (Instant.tryParse(date).takeIf { it != 0L } ?: DATE_FORMAT.tryParseDate(date).takeIf { it != 0L })
            ?.let { chapter.date_upload = it }
    }

    private fun meta(name: String) = packageDocument.selectFirst("*|metadata > *|$name")?.textOrNull()

    private fun pagePaths(): List<String> {
        val pages = packageDocument.select("*|manifest > *|item[media-type=application/xhtml+xml]").associateBy { it.attr("id") }
        return packageDocument.select("*|spine > *|itemref")
            .mapNotNull { pages[it.attr("idref")]?.attr("href") }
            .mapNotNull { resolve(packagePath, it) }
    }

    private suspend fun pageImages(pagePath: String): List<Entry> {
        val page = entries[pagePath] ?: return emptyList()
        return share.parse(path, page).select("*|img[src], *|image").mapNotNull { image ->
            val href = image.attr("src").ifEmpty { image.attr("xlink:href") }.ifEmpty { image.attr("href") }
            resolve(pagePath, href)?.let(entries::get)
        }
    }

    companion object {
        private const val CONTAINER = "META-INF/container.xml"
        private const val DEFAULT_PACKAGE = "OEBPS/content.opf"
        private const val PAGE_READS = 8

        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

        suspend fun open(share: Share, path: String): Epub? {
            val entries = share.zipDirectory(path).entries.associateBy { it.name }
            val packagePath = entries[CONTAINER]
                ?.let { share.parse(path, it).selectFirst("*|rootfile[full-path]")?.attr("full-path") }
                ?: DEFAULT_PACKAGE
            val packageEntry = entries[packagePath] ?: return null
            return Epub(share, path, entries, packagePath, share.parse(path, packageEntry))
        }

        private suspend fun Share.parse(path: String, entry: Entry): Document = readZipEntry(path, entry).buffer().use {
            Jsoup.parse(it.inputStream(), "UTF-8", "", Parser.xmlParser())
        }

        /** [href] relative to the entry at [from], as an entry name; percent-escapes decoded, fragment dropped */
        private fun resolve(from: String, href: String): String? {
            val base = HttpUrl.Builder().scheme("https").host("epub.invalid").apply {
                from.split('/').dropLast(1).forEach { addPathSegment(it) }
                addPathSegment("")
            }.build()
            return base.resolve(href)?.takeIf { it.host == base.host }?.pathSegments?.joinToString("/")
        }
    }
}
