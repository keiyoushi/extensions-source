package eu.kanade.tachiyomi.extension.en.arknightscomics

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// manga and chapter urls mirror the site routes (/comic and /comic-detail),
// so the default getMangaUrl/getChapterUrl open the right webview page
@Source
abstract class ArknightsComics : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchComics().map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val comics = fetchComics().filter { it.name.contains(query, ignoreCase = true) }
        return MangasPage(comics.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val name = url.queryParameter("name") ?: return null
        return fetchComics().firstOrNull { it.name == name }?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val name = comicName(manga.url)
        val details = if (fetchDetails) async { fetchComics().first { it.name == name }.toSManga() } else null
        val chapterList = if (fetchChapters) {
            // row order differs between comics, so sort by the chapter number instead
            async { fetchChapters(name).map { it.toSChapter() }.sortedByDescending { it.chapter_number } }
        } else {
            null
        }

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        return fetchChapters(comicName(chapter.url), chapterUrl.queryParameter("chapter"))
            .flatMap { it.detailsContent }
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private suspend fun fetchComics(): List<Comic> = client.get("$baseUrl/api/resource/comic/list").parseAs<ApiResponse<Comic>>().data.rows

    // index and size are required, the site itself asks for size=9999 to get every chapter at once
    private suspend fun fetchChapters(name: String, chapter: String? = null): List<ComicChapter> {
        val url = "$baseUrl/api/resource/comic/details".toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("index", "1")
            .addQueryParameter("size", "9999")
            .apply { if (chapter != null) addQueryParameter("chapter", chapter) }
            .build()
        return client.get(url).parseAs<ApiResponse<ComicChapter>>().data.rows
    }

    private fun comicName(url: String): String = (baseUrl + url).toHttpUrl().queryParameter("name")!!
}
