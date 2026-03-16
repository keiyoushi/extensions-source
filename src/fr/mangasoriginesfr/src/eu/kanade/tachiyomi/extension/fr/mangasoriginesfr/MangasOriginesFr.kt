package eu.kanade.tachiyomi.extension.fr.mangasoriginesfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangasOriginesFr :
    Madara(
        "Mangas-Origines.fr",
        "https://mangas-origines.fr",
        "fr",
        SimpleDateFormat("MMMM d, yyyy", Locale("fr")),
    ) {
    override val mangaSubString = "catalogues"

    override val useNewChapterEndpoint = true

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")

            val xhrRequest = POST("$mangaUrl/ajax/chapters/", xhrHeaders)
            val xhrResponse = client.newCall(xhrRequest).execute()

            chapterElements = xhrResponse.asJsoup().select(chapterListSelector())
            xhrResponse.close()
        }

        return chapterElements.map(::chapterFromElement)
    }

    // Manga Details Selectors
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
    override val mangaDetailsSelectorDescription = "div.summary__content > p"
}
