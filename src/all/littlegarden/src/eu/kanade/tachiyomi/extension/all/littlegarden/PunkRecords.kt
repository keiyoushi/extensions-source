package eu.kanade.tachiyomi.extension.all.littlegarden

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class PunkRecords : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getCatalog(page, "popular")

    override suspend fun getLatestUpdates(page: Int) = getCatalog(page, "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl("search")
            .addQueryParameter("q", query.trim())
            .addPage(page)
            .build()
        return getMangas(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private suspend fun getCatalog(page: Int, sort: String): MangasPage {
        val url = apiUrl("mangas")
            .addQueryParameter("sort", sort)
            .addPage(page)
            .build()
        return getMangas(url)
    }

    private suspend fun getMangas(url: HttpUrl): MangasPage {
        val data = client.get(url, Fetch.XHR).parseAs<MangaListDto>()
        return MangasPage(data.items.map { it.toSManga(baseUrl) }, data.page < data.totalPages)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = apiUrl("mangas").addPathSegment(manga.url.removePrefix("/")).build()
        val data = client.get(url, Fetch.XHR).parseAs<MangaDetailsDto>()
        return SMangaUpdate(
            data.manga.toSManga(baseUrl),
            data.chapters.reversed().map { it.toSChapter(data.manga.slug) },
        )
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga${manga.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaSlug, chapterSlug) = chapter.url.removePrefix("/").split("/", limit = 2)
        val url = apiUrl("mangas")
            .addPathSegment(mangaSlug)
            .addPathSegment("chapters")
            .addPathSegment(chapterSlug)
            .addPathSegment("reader")
            .build()
        return client.get(url, Fetch.XHR).parseAs<ReaderDto>().pages
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }

    private fun apiUrl(path: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("api")
        .addPathSegment(path)

    private fun HttpUrl.Builder.addPage(page: Int) = addQueryParameter("page", page.toString())
        .addQueryParameter("perPage", "24")
}
