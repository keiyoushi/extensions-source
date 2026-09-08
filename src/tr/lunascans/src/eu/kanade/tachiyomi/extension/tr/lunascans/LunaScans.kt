package eu.kanade.tachiyomi.extension.tr.lunascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LunaScans : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.AdminAjax

    override fun parsePages(document: Document): List<Page> {
        val pageList = super.parsePages(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return pageList
    }
}
