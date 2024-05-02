package eu.kanade.tachiyomi.extension.tr.yaoitr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class YaoiTR : Madara(
    "Yaoi TR",
    "https://yaoitr.fun",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
