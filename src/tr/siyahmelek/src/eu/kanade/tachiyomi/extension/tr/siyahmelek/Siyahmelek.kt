package eu.kanade.tachiyomi.extension.tr.siyahmelek

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Siyahmelek : Madara(
    "Gri Melek",
    "https://grimelek.me",
    "tr",
    SimpleDateFormat("dd MMM yyyy", Locale("tr")),
) {
    // Siyahmelek (tr) -> Gri Melek (tr)
    override val id = 6419959498055001014

    override val mangaSubString = "seri"

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    // Skip fake image
    // OK: <div class="page-break no-gaps">
    // NG: <div style="display:none" class="page-break no-gaps">
    override val pageListParseSelector = "div.page-break:not([style*=\"display:\"])"

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.request.url.encodedPath == "/giris-yapiniz/") {
            throw Exception("WebView'de oturum açarak erişin")
        }
        return super.mangaDetailsParse(response)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.encodedPath == "/giris-yapiniz/") {
            throw Exception("WebView'de oturum açarak erişin")
        }
        return super.chapterListParse(response)
    }
}
