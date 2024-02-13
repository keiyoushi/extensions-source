package eu.kanade.tachiyomi.extension.en.tappytoonnet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class Tappytoonnet : Madara("TappyToon.Net", "https://tappytoon.net", "en", SimpleDateFormat("MMMM d, yyyy", Locale.US)) {
    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/all-mangas/${pagePath(page)}?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/all-mangas/${pagePath(page)}?m_orderby=latest", headers)
}
