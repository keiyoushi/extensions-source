package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape : Madara(
    "StoneScape",
    "https://stonescape.xyz",
    "en",
    SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
) {
    override val mangaSubString = "series"

    override val chapterUrlSelector = "li > a"

    override val mangaDetailsSelectorAuthor = ".post-content .manga-authors a"
    override val mangaDetailsSelectorDescription = ".manga-about, ${super.mangaDetailsSelectorDescription}"

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
