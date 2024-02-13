package eu.kanade.tachiyomi.extension.tr.evascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class EvaScans : Madara(
    "ManWe",
    "https://manwe.pro",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr")),
) {
    override val id = 5223802501310383833

    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun pageListParse(document: Document): List<Page> {
        val pageList = super.pageListParse(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return pageList
    }
}
