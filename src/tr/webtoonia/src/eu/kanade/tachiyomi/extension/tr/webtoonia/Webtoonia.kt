package eu.kanade.tachiyomi.extension.tr.webtoonia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class Webtoonia : Madara() {
    override val chapterMode = ChapterMode.MangaAjax

    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst(".login-required") != null) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.parsePages(document)
    }
}
