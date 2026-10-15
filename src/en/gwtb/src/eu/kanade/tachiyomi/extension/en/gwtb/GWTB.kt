package eu.kanade.tachiyomi.extension.en.gwtb

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
abstract class GWTB : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = SManga.create().apply {
        title = name
        url = "/index.php"
        author = "Kimmo Lemetti"
        artist = "Kimmo Lemetti"
        thumbnail_url = "$baseUrl/images/yarr.jpg"
        description = "Because war can be boring too."
    }.let { MangasPage(listOf(it), false) }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedChapters = if (fetchChapters) {
            client.get(getMangaUrl(manga)).asJsoup().select(".fall > option:not(:first-child)").map {
                SChapter.create().apply {
                    name = it.ownText()
                    url = "/index.php?nro=${it.`val`()}"
                    chapter_number = it.`val`().toFloat()
                }
            }
        } else {
            chapters
        }
        return SMangaUpdate(manga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        return listOf(Page(0, imageUrl = doc.selectFirst(".comic_title + img")!!.absUrl("src")))
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
}
