package eu.kanade.tachiyomi.extension.ja.mangakoma

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class MangaKoma : Liliana("Manga Koma", "https://mangakoma01.net", "ja") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.separator[data-index]").map { page ->
            val index = page.attr("data-index").toInt()
            val url = page.selectFirst("a")!!.attr("abs:href")
            Page(index, document.location(), url)
        }.sortedBy { it.index }
    }
}
