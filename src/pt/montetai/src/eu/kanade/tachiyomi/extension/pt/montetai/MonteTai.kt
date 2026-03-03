package eu.kanade.tachiyomi.extension.pt.montetai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MonteTai :
    Madara(
        "Monte Tai",
        "https://montetaiscanlator.xyz",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()

    override val mangaDetailsSelectorThumbnail = ".mtx-cover img"
    override val mangaDetailsSelectorAuthor = ".mtx-side-item:contains(Autor) .mtx-side-value"
    override val mangaDetailsSelectorArtist = ".mtx-side-item:contains(Artista) .mtx-side-value"
    override val mangaDetailsSelectorGenre = ".mtx-chip-list a"
    override val mangaDetailsSelectorDescription = ".entry-content.entry-content-single"
    override val mangaDetailsSelectorStatus = ".mtx-pill-status"

    override fun chapterListSelector() = ".mtx-chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".mtx-chapter-title")!!.text()
        date_upload = dateFormat.tryParse(element.selectFirst(".mtx-chapter-meta")?.text())
        setUrlWithoutDomain(element.absUrl("href"))
    }
}
