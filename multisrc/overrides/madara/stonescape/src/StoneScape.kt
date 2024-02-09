package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape : Madara(
    "StoneScape",
    "https://stonescape.xyz",
    "en",
    SimpleDateFormat("MMMM dd, yyyy", Locale("en")),
) {
    override val mangaSubString = "series"
    override val chapterUrlSelector = "div + a"
}
