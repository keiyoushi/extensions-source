package eu.kanade.tachiyomi.extension.vi.hentaicube

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiCB : Madara("CBHentai", "https://hentaicube.xyz", "vi", SimpleDateFormat("dd/MM/yyyy", Locale("vi"))) {

    override val id: Long = 823638192569572166

    override val mangaSubString = "read"

    override val filterNonMangaItems = false

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).distinctBy { it.imageUrl }
    }
}
