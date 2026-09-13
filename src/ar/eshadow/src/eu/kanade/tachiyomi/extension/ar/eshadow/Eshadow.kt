package eu.kanade.tachiyomi.extension.ar.eshadow

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Eshadow : KeiSource() {

    override val supportsLatest = false

    private suspend fun getMangas(page: Int, query: String? = null) = "$baseUrl/api/manga".toHttpUrl().newBuilder().apply {
        addQueryParameter("page", page.toString())
        if (query != null) addQueryParameter("query", query)
    }.build()
        .let { client.get(it).parseAs<MangaList>().toMangasPage() }

    override suspend fun getPopularManga(page: Int) = getMangas(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getMangas(page, query)

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]!!.string
        return "$baseUrl/manga/$slug"
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/read/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl/api/manga/${manga.url}")
        val mangaDto = response.parseAs<Manga>()

        val updatedChapters = mangaDto.chapters.map {
            it.toSChapter()
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(mangaDto.toSManga(), updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = chapter.memo["pages"]!!.parseAs<List<String>>()
        return images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }
}
