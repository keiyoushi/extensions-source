package eu.kanade.tachiyomi.extension.tr.araznovel

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ArazNovel : Madara("ArazNovel", "https://www.araznovel.com", "tr", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaUrl = document.location().removeSuffix("/")

        val xhrRequest = xhrChaptersRequest(mangaUrl)
        val xhrResponse = client.newCall(xhrRequest).execute()

        val chapterElements = xhrResponse.asJsoup().select("ul.version-chap li.wp-manga-chapter")
        return chapterElements.map(::chapterFromElement)
    }
}
