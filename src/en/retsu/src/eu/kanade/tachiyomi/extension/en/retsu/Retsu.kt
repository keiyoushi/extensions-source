package eu.kanade.tachiyomi.extension.en.retsu

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Retsu : Madara(
    "Retsu",
    "https://retsu.org",
    "en",
) {
    override fun popularMangaSelector() = "div.manga__item"
    override val popularMangaUrlSelector = "h4 a"

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorGenre = "div.manga-genres a"
    override val seriesTypeSelector = ".manga-type .summary-content"
    override val altNameSelector = ".manga-alternative"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false

    override fun searchMangaSelector() = ".manga__item"
    override val searchMangaUrlSelector = ".post-title a"
}
