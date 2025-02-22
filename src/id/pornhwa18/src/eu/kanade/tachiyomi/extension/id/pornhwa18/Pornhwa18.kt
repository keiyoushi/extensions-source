package eu.kanade.tachiyomi.extension.id.pornhwa18

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class Pornhwa18 : Madara("Pornhwa18", "https://pornhwa18.com", "id") {
    override val filterNonMangaItems = false
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series/page/$page/?m_orderby=latest", headers)
}
