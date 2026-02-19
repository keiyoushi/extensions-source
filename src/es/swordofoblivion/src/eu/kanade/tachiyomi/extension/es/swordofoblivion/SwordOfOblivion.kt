package eu.kanade.tachiyomi.extension.es.swordofoblivion

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SwordOfOblivion :
    Madara(
        "Sword Of Oblivion",
        "https://swordofoblivion.com",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
