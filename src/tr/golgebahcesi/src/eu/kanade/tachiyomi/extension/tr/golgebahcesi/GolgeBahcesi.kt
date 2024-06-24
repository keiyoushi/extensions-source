package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class GolgeBahcesi : MangaThemesia(
    "Gölge Bahçesi",
    "https://golgebahcesi.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    private var captchaUrl: String? = null

    override fun getMangaUrl(manga: SManga): String =
        captchaUrl?.also { captchaUrl = null }
            ?: super.getMangaUrl(manga)

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst("#readerarea form, #readerarea input[value=Doğrula]") != null) {
            captchaUrl = document.selectFirst("#readerarea form")
                ?.attr("abs:action")
                ?: "$baseUrl/kontrol/"
            throw Exception("WebView'da captcha çözün.")
        }
        return super.pageListParse(document)
    }
}
