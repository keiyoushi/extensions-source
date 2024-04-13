package eu.kanade.tachiyomi.extension.es.templescanesp

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter

class TempleScanEsp : MangaEsp("Temple Scan", "https://templescanesp.net", "es") {

    // Site moved from custom theme to MangaEsp
    override val versionId = 3

    private val readerUrl = "https://templescanesp.xyz"

    override fun pageListRequest(chapter: SChapter) = GET(readerUrl + chapter.url, headers)
}
