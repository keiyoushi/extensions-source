package eu.kanade.tachiyomi.extension.tr.hentaing

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Hentaing : Madara(
    "Hentaing",
    "https://hentaing.org",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        val pageList = super.pageListParse(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }

        return pageList.filterNot { it.imageUrl?.let(patternBadImage::containsMatchIn) ?: true }
    }

    private val patternBadImage = """/\.(?:webp|jpeg|tiff|.{3})$""".toRegex()
}
