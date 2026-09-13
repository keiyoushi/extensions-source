package eu.kanade.tachiyomi.extension.id.otascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class OtaScans : Madara() {
    override val mangaSubString = "series"
    override fun archiveSelector() = "div.manga__item"
    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val chapterMode = ChapterMode.MangaAjaxPaginated
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    override fun parseArchive(document: Document) = document.select(archiveSelector()).mapNotNull { element ->
        val link = element.selectFirst(archiveUrlSelector) ?: return@mapNotNull null
        val slug = link.attr("abs:href").toHttpUrl().pathSegments.lastOrNull(String::isNotBlank) ?: return@mapNotNull null
        archiveManga(element, slug)
    }

    override fun parseChapterDate(date: String?): Long {
        val parsed = super.parseChapterDate(date)
        if (parsed != 0L) return parsed

        val cleanDate = date?.trim() ?: return 0L
        val currentDate = LocalDate.now()
        val parser = DateTimeFormatterBuilder()
            .appendPattern("d MMMM")
            .parseDefaulting(ChronoField.YEAR, currentDate.year.toLong())
            .toFormatter(Locale.ENGLISH)
        val parsedDate = runCatching { LocalDate.parse(cleanDate, parser) }.getOrNull() ?: return 0L
        return parsedDate
            .let { if (it.isAfter(currentDate)) it.minusYears(1) else it }
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
}
