package eu.kanade.tachiyomi.extension.th.reborntrans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response

class RebornTrans : HttpSource() {
    override val name = "Reborn Trans"
    override val baseUrl = "https://reborntrans.com"
    override val lang = "th"
    override val supportsLatest = true

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
