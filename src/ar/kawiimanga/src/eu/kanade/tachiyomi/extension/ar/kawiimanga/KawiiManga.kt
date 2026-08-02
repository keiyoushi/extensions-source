package eu.kanade.tachiyomi.extension.ar.kawiimanga

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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class KawiiManga : KeiSource() {
    private val apiUrl = "https://manga-api.kawaii-anime.com/api/manga/own"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("x-app-key", "km_2026_live")
    }

    private fun Response.toMangasPage(): MangasPage {
        val data = this.parseAs<MangaList>()
        val entries = data.results.map { it.toSManga() }
        return MangasPage(entries, data.hasMore)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$apiUrl?action=browse&page=$page&sort=views")
        return response.toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$apiUrl?action=browse&page=$page")
        return response.toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("action", "search")
            addQueryParameter("q", query)
        }.build()

        return client.get(url).toMangasPage()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun getMangaByUrl(url: okhttp3.HttpUrl): SManga? {
        check(url.pathSegments.size >= 2) { "Unsupported URL" }
        val slug = url.pathSegments[1]
        val response = client.get("$apiUrl?action=series&slug=$slug")
        return response.parseAs<Manga>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url
        val response = client.get("$apiUrl?action=series&slug=$slug")
        val entrie = response.parseAs<Manga>()

        return SMangaUpdate(
            entrie.toSManga(),
            entrie.chapters.map { it.toSChapter(slug) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url.substringBeforeLast("#")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('#')
        val response = client.get("$apiUrl?action=pages&chapterId=$chapterId")
        return response.parseAs<Pages>().pages.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }
}
