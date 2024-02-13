package eu.kanade.tachiyomi.extension.en.milftoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class Milftoon : Madara("Milftoon", "https://milftoon.xxx", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=latest", headers)
}
