package eu.kanade.tachiyomi.extension.tr.sacguzellik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.utils.MarkdownDescription
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SacGuzellik : Madara() {
    override val mangaSubString = "seri-oku"
    override val genreDirectory = "seri-turleri"
    override val filterGenresSelector = ".mgcr-modern-archive__genre-cloud"
    override val statusFilterOptions = super.statusFilterOptions + ("Upcoming" to "upcoming")

    override val mangaDetailsSelectorTitle = "#mgcr-series-title"
    override val mangaDetailsSelectorThumbnail = ".mgcr-modern-card__cover img"
    override val mangaDetailsSelectorStatus = ".mgcr-modern-card__fact:has(span:containsOwn(Durum)) strong"
    override val mangaDetailsSelectorGenre = ".mgcr-modern-card__genres a"
    override val seriesTypeSelector = ".mgcr-modern-card__fact:has(span:containsOwn(Tür)) strong"

    override val chapterUrlSelector = "a:not(:has(img))"
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.replace(THUMBNAIL_SIZE_REGEX, "$1")

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl).apply {
        val altTitles = document.selectFirst(".mgcr-modern-manga__alternative")?.text()
            ?.takeIf { it != title }?.split(", ").orEmpty()
        description = MarkdownDescription.Builder()
            .rating(
                document.selectFirst("#averagerate")?.text()?.toDoubleOrNull(),
                max = 5,
                votes = document.selectFirst("#countrate")?.text()?.toIntOrNull(),
            )
            .info(document.selectFirst(".mgcr-modern-card__fact:has(span:containsOwn(Yayın Tarihi)) strong")?.text())
            .description(document.selectFirst(mangaDetailsSelectorDescription))
            .altTitles(altTitles)
            .build()
    }

    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst(".reading-content .mgcr-access-card") != null) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.parsePages(document)
    }

    companion object {
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(\.[a-zA-Z]+)$""")
    }
}
