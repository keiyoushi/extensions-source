package eu.kanade.tachiyomi.extension.th.mangadeemak

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDeemak : Madara("MangaDeemak", "https://mangadeemak.com", "th", SimpleDateFormat("d MMMM yyyy", Locale("th"))) {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/", headers)
    }
}
