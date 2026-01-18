package eu.kanade.tachiyomi.extension.en.novel24h

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList

class Novel24h : Madara("24HNovel", "https://24hnovel.com", "en") {

    override val mangaSubString: String = "manga-tag/comic"

    override fun searchMangaSelector(): String = "div.page-item-detail.manga"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/$mangaSubString/${searchPage(page)}?s=$query", headers)
}
