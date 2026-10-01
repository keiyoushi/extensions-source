package eu.kanade.tachiyomi.extension.en.mlbblore

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.FormBody

private const val SORT_NEWEST = 1
private const val SORT_POPULARITY = 3

@Source
abstract class MLBBLore : KeiSource() {

    private val apiUrl = "https://api.mobilelegends.com"
    private val pageSize = 5

    private suspend fun fetchDetail(id: String): AlbumDetail? {
        val body = FormBody.Builder()
            .add("id", id)
            .add("lang", "en")
            .add("token", "")
            .build()

        return client.post("$apiUrl/lore/album/detail", body).parseAs<ApiDetailResponse>().data
    }

    private suspend fun fetchMangaList(page: Int, sort: Int): MangasPage {
        val body = FormBody.Builder()
            .add("type", TYPE_COMIC.toString())
            .add("sort", sort.toString())
            .add("page", page.toString())
            .add("page_size", pageSize.toString())
            .add("lang", "en")
            .add("token", "")
            .build()

        val result = client.post("$apiUrl/lore/album/list", body).parseAs<ApiListResponse>()
        val mangas = result.data.filter { it.isComic() }.map { it.toSManga() }
        return MangasPage(mangas, result.data.size >= pageSize)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, SORT_POPULARITY)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(page, SORT_NEWEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(page)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = fetchDetail(manga.url)
            ?: return SMangaUpdate(manga, emptyList())

        return SMangaUpdate(
            manga = detail.toSManga().apply { url = manga.url },
            chapters = listOf(detail.toSChapter()),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchDetail(chapter.url)?.toPageList() ?: emptyList()
}
