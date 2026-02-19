package eu.kanade.tachiyomi.extension.es.houseofotakus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HouseOfOtakus :
    Madara(
        "House Of Otakus",
        "https://houseofotakusv2.xyz",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
