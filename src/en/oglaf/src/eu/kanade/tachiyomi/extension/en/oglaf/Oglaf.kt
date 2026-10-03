package eu.kanade.tachiyomi.extension.en.oglaf

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
abstract class Oglaf : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = SManga.create().apply {
            title = "Oglaf"
            artist = "Trudy Cooper & Doug Bayne"
            author = "Trudy Cooper & Doug Bayne"
            status = SManga.ONGOING
            url = "/archive/"
            description = "Filth and other Fantastical Things in handy webcomic form."
            thumbnail_url = "https://i.ibb.co/tzY0VQ9/oglaf.png"
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

        val document = client.get(baseUrl + manga.url).asJsoup()
        val chapterList = document.select("a:has(img[width=400])").mapNotNull { element ->
            val href = element.attr("href")
            val nameMatch = nameRegex.find(href) ?: return@mapNotNull null

            SChapter.create().apply {
                url = href
                name = nameMatch.groupValues[1]
            }
        }.distinct()

        return SMangaUpdate(
            manga,
            chapterList.mapIndexed { i, ch ->
                ch.apply { chapter_number = chapterList.size.toFloat() - i }
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = mutableListOf<Page>()
        var document = client.get(baseUrl + chapter.url).asJsoup()

        while (true) {
            val imageUrl = document.selectFirst("img#strip")?.attr("abs:src") ?: break
            pages.add(Page(pages.size, imageUrl = imageUrl))

            val nextUrl = document.selectFirst("a[rel=next]")?.attr("href")
            if (nextUrl != null && urlRegex.matches(nextUrl)) {
                document = client.get(baseUrl + nextUrl).asJsoup()
            } else {
                break
            }
        }

        return pages
    }

    companion object {
        private val nameRegex = """/(.*)/""".toRegex()
        private val urlRegex = """/.*/\d*/""".toRegex()
    }
}
