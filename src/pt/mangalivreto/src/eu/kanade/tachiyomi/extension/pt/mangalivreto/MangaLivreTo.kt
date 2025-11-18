package eu.kanade.tachiyomi.extension.pt.mangalivreto

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivreTo : Madara(
    "Manga Livre.to",
    "https://mangalivre.to",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun chapterListSelector() = ".listing-chapters-wrap .chapter-box"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(absUrl("href"))
        }
        date_upload = parseChapterDate(element.selectFirst(".chapter-date")?.text())
    }
}
