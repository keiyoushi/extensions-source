package eu.kanade.tachiyomi.extension.tr.turkcemangaokutr

import eu.kanade.tachiyomi.multisrc.madara.Madara

class TurkceMangaOkuTr :
    Madara(
        "Türkçe Manga Oku TR",
        "https://turkcemangaoku.com.tr",
        "tr",
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
