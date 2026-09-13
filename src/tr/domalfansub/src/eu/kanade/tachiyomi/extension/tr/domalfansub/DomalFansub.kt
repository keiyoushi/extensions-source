package eu.kanade.tachiyomi.extension.tr.domalfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DomalFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Durum) + div.summary-content"
    override val chapterMode = ChapterMode.MangaAjax

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga {
        checkLogin(document)
        return super.parseDetails(document, id, preserveUrl)
    }

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        checkLogin(document)
        return super.parseChapterList(document, mangaPath)
    }

    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst(".login-required") != null) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.parsePages(document)
    }

    private fun checkLogin(document: Document) {
        if (document.location().contains("/giris-korumasi")) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
    }
}
