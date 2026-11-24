package eu.kanade.tachiyomi.extension.all.networkshare

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import okio.BufferedSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.time.YearMonth
import java.time.ZoneId

/** A ComicInfo.xml, mapped to the fields the app's Local source fills from it. */
class ComicInfo(private val document: Document) {
    fun applyTo(manga: SManga) {
        field("Series")?.let { manga.title = it }
        field("Writer")?.let { manga.author = it }
        field("Summary", keepLines = true)?.let { manga.description = MarkdownDescription.Builder().description(it).build() }
        listOfNotNull(field("Genre"), field("Tags"), field("Categories"))
            .distinct()
            .joinToString()
            .takeIf { it.isNotEmpty() }
            ?.let { manga.genre = it }
        listOfNotNull(field("Penciller"), field("Inker"), field("Colorist"), field("Letterer"), field("CoverArtist"))
            .flatMap { it.split(", ") }
            .distinct()
            .joinToString { it.trim() }
            .takeIf { it.isNotEmpty() }
            ?.let { manga.artist = it }
        manga.status = when (field("PublishingStatusTachiyomi")) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Licensed" -> SManga.LICENSED
            "Publishing finished" -> SManga.PUBLISHING_FINISHED
            "Cancelled" -> SManga.CANCELLED
            "On hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    fun applyTo(chapter: SChapter) {
        field("Title")?.let { chapter.name = it }
        field("Number")?.toFloatOrNull()?.let { chapter.chapter_number = it }
        field("Translator")?.let { chapter.scanlator = it }
        val year = field("Year")?.toIntOrNull()?.takeIf { it > 0 } ?: return
        val month = YearMonth.of(year, field("Month")?.toIntOrNull()?.coerceIn(1, 12) ?: 1)
        val day = field("Day")?.toIntOrNull()?.coerceIn(1, month.lengthOfMonth()) ?: 1
        chapter.date_upload = month.atDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun field(name: String, keepLines: Boolean = false): String? = document.selectFirst("ComicInfo > *|$name")
        ?.let { if (keepLines) it.wholeText() else it.text() }
        ?.takeIf { it.isNotBlank() }

    companion object {
        const val FILE_NAME = "ComicInfo.xml"

        fun parse(source: BufferedSource): ComicInfo = source.use {
            ComicInfo(Jsoup.parse(it.inputStream(), "UTF-8", "", Parser.xmlParser()))
        }
    }
}
