package eu.kanade.tachiyomi.extension.ar.onma

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class Onma : MMRCMS(
    "مانجا اون لاين",
    "https://onma.top",
    "ar",
    detailsTitleSelector = ".panel-heading",
) {
    override fun searchMangaSelector() = "div.chapter-container"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            document.select(".panel-body h3").forEach { element ->
                when (element.ownText().lowercase().removeSuffix(" :")) {
                    in detailAuthor -> author = element.selectFirst("div.text")!!.text()
                    in detailArtist -> artist = element.selectFirst("div.text")!!.text()
                    in detailGenre -> genre = element.select("div.text a").joinToString { it.text() }
                    in detailStatus -> status = when (element.selectFirst("div.text")!!.text()) {
                        in detailStatusComplete -> SManga.COMPLETED
                        in detailStatusOngoing -> SManga.ONGOING
                        in detailStatusDropped -> SManga.CANCELLED
                        else -> SManga.UNKNOWN
                    }
                }
            }
        }
    }
}
