package eu.kanade.tachiyomi.extension.en.mangadrama

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParseDateTime
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

@Source
abstract class MangaDrama : InitManga() {

    override val mangaUrlDirectory = "manga"
    override val popularUrlSlug = "manga-ranking"
    override val latestUrlSlug = "recently-updated"
    override val chapterPagePathSegment = "chapter"

    override val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("MMMM d, yyyy h:mm a")
        .toFormatter(Locale.ENGLISH)

    override fun parseMangaDetails(document: Document) = super.parseMangaDetails(document).apply {
        author = document.infoValue("Author")
        artist = document.infoValue("Illustrator")
        genre = document.select("div#genre-tags a:not(.uk-disabled)").joinToString { it.text() }
    }

    private fun Document.infoValue(label: String): String? = selectFirst("div.manga-info-details")
        ?.textNodes()
        ?.firstOrNull { it.text().trim() == "$label:" }
        ?.let { it.nextSibling() as? Element }
        ?.text()

    override fun chapterListSelector() = "div.chapter-list > div"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        name = link.selectFirst(".uk-flex-none")!!.text()
        val date = link.selectFirst("[uk-tooltip]")?.attr("uk-tooltip")
            ?.substringAfter("title:")?.substringBefore(";")?.trim()
        date_upload = dateFormat.tryParseDateTime(date)
    }
}
