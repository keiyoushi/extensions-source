package eu.kanade.tachiyomi.extension.en.existentialcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup

@Source
abstract class ExistentialComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = SManga.create().apply {
            title = "Existential Comics"
            artist = "Corey Mohler"
            author = "Corey Mohler"
            status = SManga.ONGOING
            url = "/archive/byDate"
            description = "A philosophy comic about the inevitable anguish of living a brief life in an absurd world. Also Jokes."
            thumbnail_url = "https://i.ibb.co/pykMVYM/existential-comics.png"
        }

        return MangasPage(listOf(manga), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val chapterList = client.get(getMangaUrl(manga)).asJsoup().select("div#date-comics ul li a:eq(0)").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.text()
                chapter_number = url.substringAfterLast("/").toFloatOrNull() ?: 0f
            }
        }.distinctBy { it.url }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select(".comicImg").mapIndexed { i, element ->
        Page(i, imageUrl = element.attr("abs:src"))
    }
}
