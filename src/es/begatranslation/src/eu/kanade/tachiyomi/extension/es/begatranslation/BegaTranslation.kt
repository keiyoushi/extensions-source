package eu.kanade.tachiyomi.extension.es.begatranslation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class BegaTranslation : Madara(
    "Bega Translation",
    "https://begatranslation.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "series"

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            thumbnail_url = thumbnail_url?.replaceFirst("-175x238", "")
        }
    }
    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            thumbnail_url = thumbnail_url?.replaceFirst("-193x278", "")
        }
    }
}
