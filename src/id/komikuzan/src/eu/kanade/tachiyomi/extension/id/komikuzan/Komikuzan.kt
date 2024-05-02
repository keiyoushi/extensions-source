package eu.kanade.tachiyomi.extension.id.komikuzan

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Komikuzan : Madara(
    "Komikuzan",
    "https://komikuzan.com",
    "id",
    // dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
