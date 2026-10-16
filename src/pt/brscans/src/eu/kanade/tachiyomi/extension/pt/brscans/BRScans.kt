package eu.kanade.tachiyomi.extension.pt.brscans

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
abstract class BRScans : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$API_URL/manhwas/top/").parseAs<List<ManhwaDto>>()
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$API_URL/manhwas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val data = client.get(url).parseAs<ManhwaPageDto>()
        return MangasPage(data.results.map { it.toSManga() }, data.next != null)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$API_URL/manhwas/search/".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .build()
        val mangas = client.get(url).parseAs<List<ManhwaDto>>()
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = getMangaUrl(manga).toHttpUrl().pathSegments[1]
        val data = client.get("$API_URL/manhwas/$id/").parseAs<ManhwaDto>()
        return SMangaUpdate(
            data.toSManga(),
            data.chapters.reversed().flatMap { chapter -> Variant.entries.map { chapter.toSChapter(manga.url, it) } },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        val variant = Variant.entries.first { it.key == url.queryParameter("variant") }
        val pages = client.get("$API_URL/chapters/${url.pathSegments.last()}/").parseAs<ChapterPagesDto>().pages
        return pages.mapNotNull { it.images.url(variant) }
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    companion object {
        private const val API_URL = "https://e5oer7ngt8.execute-api.sa-east-1.amazonaws.com/dev"
    }
}
