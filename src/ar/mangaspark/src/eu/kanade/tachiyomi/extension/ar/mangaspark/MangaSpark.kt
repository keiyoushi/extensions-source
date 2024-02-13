package eu.kanade.tachiyomi.extension.ar.mangaspark

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSpark : Madara(
    "MangaSpark",
    "https://mangaspark.org",
    "ar",
    dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar")),
) {
    override val chapterUrlSuffix = ""

    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
