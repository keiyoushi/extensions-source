package eu.kanade.tachiyomi.extension.id.koronaxscan

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class KoronaxScan : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangas().sortedByDescending { it.ratingValue }.toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangas().sortedByDescending { it.latestChapterAt }.toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchMangas().filter {
        it.title.contains(query, ignoreCase = true) || it.titleAlt?.contains(query, ignoreCase = true) == true
    }.toMangasPage()

    // the site has no paginated listing; it fetches every title once and sorts/searches client-side
    private suspend fun fetchMangas(): List<MangaDto> = client.get("$API_URL/api/mangas").parseAs<MangaListDto>().data.filter { it.isListed }

    private fun List<MangaDto>.toMangasPage() = MangasPage(map { it.toSManga() }, false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = fetchManga(manga.url)
        return SMangaUpdate(detail.toSManga(), detail.chapters.map { it.toSChapter(detail.slug) })
    }

    private suspend fun fetchManga(slug: String): MangaDto = client.get("$API_URL/api/mangas/$slug").parseAs<MangaDetailDto>().data

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.substringBefore('/')
        val number = chapter.url.substringAfter('/')
        return client.get("$API_URL/api/mangas/$slug/chapter/$number").parseAs<PagesDto>().pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrl.toImageUrl())
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/detail/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.takeIf { it.firstOrNull() in MANGA_PATHS }?.getOrNull(1) ?: return null
        return fetchManga(slug).toSManga()
    }

    companion object {
        const val API_URL = "https://asw.koronaxscan.my.id"
        private val MANGA_PATHS = setOf("detail", "episode", "reader")
    }
}
