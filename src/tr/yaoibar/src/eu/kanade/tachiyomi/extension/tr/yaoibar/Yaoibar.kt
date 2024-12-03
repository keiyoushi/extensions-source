package eu.kanade.tachiyomi.extension.tr.yaoibar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Yaoibar : Madara(
    "Yaoibar",
    "https://yaoibar.gay",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint: Boolean = true
}
