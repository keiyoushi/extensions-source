package eu.kanade.tachiyomi.extension.tr.domalfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class DomalFansub :
    Madara(
        "Domal Fansub",
        "https://dom4lfansub.online",
        "tr",
        dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
    ) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Durum) + div.summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.request.url.pathSegments.getOrNull(0) == "giris-korumasi") {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.mangaDetailsParse(response)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.pathSegments.getOrNull(0) == "giris-korumasi") {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.chapterListParse(response)
    }

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst(".login-required") != null) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.pageListParse(document)
    }
}
