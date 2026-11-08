package eu.kanade.tachiyomi.extension.es.fokascans

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
abstract class FokaScans : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getLibrary().map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getLibrary().filter { it.matches(query) }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return getLibrary().firstOrNull { it.id == id }?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val entry = getEntry(manga.url.substringAfterLast('/'))
        return SMangaUpdate(entry.toSManga(), entry.toSChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (_, entryId, chapterId) = chapter.url.removePrefix("/").split('/')
        return getEntry(entryId).pages(chapterId).mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    // Games link to another site and videos have no pages, so only manga and SBS are listed
    private suspend fun getLibrary() = client.get(API_URL, Fetch.XHR).parseAs<LibraryDto>()
        .library
        .filter { it.isReadable }

    private suspend fun getEntry(id: String) = getLibrary().first { it.id == id }

    companion object {
        private const val API_URL = "https://fokascans-api.themadrefoka.workers.dev"
    }
}
