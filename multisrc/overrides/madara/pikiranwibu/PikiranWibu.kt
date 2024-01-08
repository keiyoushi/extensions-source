package eu.kanade.tachiyomi.extension.id.pikiranwibu

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PikiranWibu : Madara(
    "Pikiran Wibu",
    "https://pikiran-wibu.com",
    "id",
    SimpleDateFormat("dd MMM yy", Locale("en")),
) {

    // popular is the latest
    override val supportsLatest = false

    override val filterNonMangaItems = false

    override val mangaSubString = ""
}
