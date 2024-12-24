package eu.kanade.tachiyomi.extension.tr.mangagezgini

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaGezgini : Madara(
    "MangaGezgini",
    "https://mangagezgini.me",
    "tr",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val chapterUrlSelector = "> a"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    private var captchaUrl: String? = null

    override fun mangaDetailsRequest(manga: SManga): Request =
        captchaUrl?.let { GET(it, headers) }.also { captchaUrl = null }
            ?: super.mangaDetailsRequest(manga)

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst(".reading-content form, .reading-content input[value=Doğrula]") != null) {
            captchaUrl = document.selectFirst(".reading-content form")
                ?.attr("abs:action")
                ?: "$baseUrl/kontrol/"
            throw Exception("WebView'da captcha çözün.")
        }
        return super.pageListParse(document)
    }
}
