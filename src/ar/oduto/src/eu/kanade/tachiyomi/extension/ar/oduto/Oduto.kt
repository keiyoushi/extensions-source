package eu.kanade.tachiyomi.extension.ar.oduto

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs

@Source
abstract class Oduto : KeiSource() {
    override val supportsLatest get() = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = SManga.create().apply {
            title = "BORUTO: Two Blue Vortex"
            artist = "Mikio Ikemoto"
            author = "Masashi Kishimoto"
            genre = "شونين, دراما, خيال, أكشن, نينجا"
            status = SManga.ONGOING
            thumbnail_url = "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEggWB9vWPMqjEvIoDsJSO29OmW-srULDQD3cS9HJ8cDk0vq2jLwDerUX-i61CqmZf62eBVmWZwU5CgXi0p2lxhKrh2_nZum3p-k3q9QJ2uozove0QAbOKtbd1QPjytjrJc9UsL65X4BbFdgcicLDYubD9LgY1Kco8wyhDGm4YEOim8u1TL42gOFe16NaaEP/s3464/4D55C3C5-9168-4103-B45C-99B52B58B6A5.jpeg"
            url = "مانجا بوروتو"
            initialized = true
        }

        return MangasPage(listOf(manga), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(page)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (fetchChapters) {
            val url = "$baseUrl/feeds/posts/summary/-/${manga.url}?alt=json&max-results=500"
            val feed = client.get(url).parseAs<BloggerFeedResponse>()
            return SMangaUpdate(manga, feed.feed.entry.map { it.toSChapter() })
        }

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select("div#post-body img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }
}
