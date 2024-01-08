package eu.kanade.tachiyomi.extension.en.astrallibrary

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class AstralLibrary : Madara("Astral Library", "https://www.astrallibrary.net", "en", SimpleDateFormat("d MMM", Locale.US)) {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-tag/manga/?m_orderby=views&page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga-tag/manga/?m_orderby=latest&page=$page", headers)
    }
}
