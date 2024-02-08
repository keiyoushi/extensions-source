package eu.kanade.tachiyomi.extension.ar.stonescape

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
}
