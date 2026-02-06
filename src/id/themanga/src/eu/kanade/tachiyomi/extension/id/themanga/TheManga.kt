package eu.kanade.tachiyomi.extension.id.themanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class TheManga : Madara("TheManga", "https://themanga.my.id", "id") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
