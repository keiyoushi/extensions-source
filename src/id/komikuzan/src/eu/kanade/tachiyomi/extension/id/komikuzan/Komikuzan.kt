package eu.kanade.tachiyomi.extension.id.komikuzan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Komikuzan : Madara(
    "Komikuzan",
    "https://komikuzan.com",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
