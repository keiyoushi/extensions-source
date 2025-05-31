package eu.kanade.tachiyomi.extension.tr.opiatoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Opiatoon : Madara(
    "Opiatoon",
    "https://opiatoon.pro",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM", Locale("tr")),
) {
    override val chapterUrlSelector = "li > a"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.request.url.pathSegments.getOrNull(0) == "giris-yapmalisiniz") {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.mangaDetailsParse(response)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.pathSegments.getOrNull(0) == "giris-yapmalisiniz") {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.chapterListParse(response)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (response.request.url.pathSegments.getOrNull(0) == "giris-yapmalisiniz") {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return super.pageListParse(response)
    }
}
