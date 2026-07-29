package eu.kanade.tachiyomi.extension.en.visionhaze

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
import okhttp3.HttpUrl
import java.lang.UnsupportedOperationException

@Source
abstract class VisionHaze : KeiSource() {
    private fun manga(): SManga = SManga.create().apply {
        title = "Vision Haze"
        thumbnail_url = "$baseUrl/_assets/media/banners/banner0.png"
        author = "Yttrium"
        status = SManga.UNKNOWN
        setUrlWithoutDomain(baseUrl)
        initialized = true
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        mangas = listOf(manga()),
        hasNextPage = false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/archive/", headers).asJsoup()
        val chapterTitles = document.select(".archive").map {
            it.select("b").text()
        }
        val chapterPayload = document.select(".archivepayload")

        val chapters = mutableListOf<SChapter>()
        chapterTitles.zip(chapterPayload).forEach { (cTitle, payload) ->
            payload.select("a").forEach {
                val url = it.absUrl("href")
                val pageNum = it.text()
                val title = "Page $pageNum - $cTitle"
                chapters.add(
                    SChapter.create().apply {
                        setUrlWithoutDomain(url)
                        name = title
                    },
                )
            }
        }

        return SMangaUpdate(
            manga = manga,
            chapters = chapters.reversed(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/${chapter.url}", headers).asJsoup()
        return document.select(".content img").mapIndexed { index, img ->
            val imgUrl = img.absUrl("src").ifEmpty { img.attr("src") }
            Page(index, imageUrl = imgUrl)
        }
    }
}
