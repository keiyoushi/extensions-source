package eu.kanade.tachiyomi.extension.en.webtoonxyz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonXYZ : Madara("WebtoonXYZ", "https://www.webtoon.xyz", "en", SimpleDateFormat("dd MMMM yyyy", Locale.US)) {
    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=latest", headers)
}
