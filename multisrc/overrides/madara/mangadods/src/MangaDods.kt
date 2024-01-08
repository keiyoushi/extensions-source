package eu.kanade.tachiyomi.extension.en.mangadods

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDods : Madara("MangaDods", "https://mangadods.com", "en", SimpleDateFormat("dd-MMM", Locale.US)) {
    override val useNewChapterEndpoint = true

    override val chapterUrlSelector = "a:not([style])"

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun chapterDateSelector() = "span i"
}
