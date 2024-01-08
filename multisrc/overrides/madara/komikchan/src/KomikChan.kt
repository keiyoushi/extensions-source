package eu.kanade.tachiyomi.extension.en.komikchan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class KomikChan : Madara("Komik Chan", "https://komikchan.com", "en") {
    override val filterNonMangaItems = false
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comics/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comics/page/$page/?m_orderby=latest", headers)
}
