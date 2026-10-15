package eu.kanade.tachiyomi.extension.tr.mangatilkisi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaTilkisi : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorTitle = "#mgcr-series-title"
    override val mangaDetailsSelectorThumbnail = ".mgcr-modern-card__cover img"
    override val mangaDetailsSelectorStatus = "div.mgcr-modern-card__fact:contains(Durum) strong"
    override val mangaDetailsSelectorGenre = ".mgcr-modern-card__genres a"
    override val altNameSelector = ".mgcr-modern-manga__alternative"

    override val chapterUrlSelector = "a:not(.chapter-thumbnail)"

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        val alternative = document.selectFirst(altNameSelector)?.ownText()
        if (alternative == title) {
            description = description?.removeSuffix("${intl["alt_names_heading"]} $alternative")?.trimEnd()?.ifEmpty { null }
        }
    }

    override suspend fun fetchChapterDocument(chapterUrl: String): Document {
        val payload = FormBody.Builder()
            .add("verified", "1")
            .build()
        return client.post(chapterUrl, headers, payload).use { it.asJsoup() }
    }
}
