package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Siimanga : Madara(
    "Siimanga",
    "https://siimanga.cyou",
    "id",
    dateFormat = SimpleDateFormat("d MMMM", Locale("en")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
