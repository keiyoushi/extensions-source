package eu.kanade.tachiyomi.extension.tr.mindafansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MindaFansub : Madara(
    "Minda Fansub",
    "https://mindafansub.pro",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
