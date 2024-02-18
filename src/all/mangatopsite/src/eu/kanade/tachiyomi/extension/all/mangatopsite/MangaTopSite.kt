package eu.kanade.tachiyomi.extension.all.mangatopsite

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTopSite : Madara(
    "MangaTop.site",
    "https://mangatop.site",
    "all",
    dateFormat = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH),
) {
    override val useNewChapterEndpoint = false
    override val chapterUrlSuffix = ""
}
