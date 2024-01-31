package eu.kanade.tachiyomi.extension.ar.olaoe

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class Olaoe : Madara(
    "Olaoe",
    "https://olaoe.cyou",
    "ar",
    SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val mangaSubString = "works"
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)" // Filter fake chapters
}
