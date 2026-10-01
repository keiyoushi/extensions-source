package eu.kanade.tachiyomi.extension.zh.terrahistoricus

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs

@Source
abstract class TerraHistoricus : KeiSource() {
    private val topicKeys = listOf("terra-historicus", "talos-ii-historicus")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val topicKey = topicKeys[page - 1]
        val comics = fetch<List<THComic>>("$baseUrl/api/comic?topicKey=$topicKey")
        return MangasPage(comics.map { it.toSManga() }, topicKey != topicKeys.last())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val topicKey = topicKeys[page - 1]
        val updates = fetch<List<THRecentUpdate>>("$baseUrl/api/recentUpdate?topicKey=$topicKey")
        return MangasPage(updates.map { it.toSManga() }, topicKey != topicKeys.last())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangasPage = getPopularManga(page)
        val mangas = mangasPage.mangas.filter { it.title.contains(query) }
        return MangasPage(mangas, mangasPage.hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.removePrefix("/api")

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.removePrefix("/api")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = fetch<THComic>(baseUrl + manga.url)
        return SMangaUpdate(comic.toSManga(), comic.toSChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episode = fetch<THEpisode>(baseUrl + chapter.url)
        return (0 until episode.pageInfos!!.size).map {
            Page(it, "$baseUrl${chapter.url}/page?pageNum=${it + 1}")
        }
    }

    override suspend fun getImageUrl(page: Page): String = fetch<THPage>(page.url).url

    private suspend inline fun <reified T> fetch(url: String) = client.get(url).parseAs<THResult<T>>().data
}
