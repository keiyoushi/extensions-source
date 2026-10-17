package eu.kanade.tachiyomi.extension.en.honkaiimpact

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
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Honkaiimpact : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(1.minutes)
        .readTimeout(1.minutes)
        .retryOnConnectionFailure(true)
        .followRedirects(true)

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/book").asJsoup().select("a[href*=book]").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = client.get("$baseUrl/book").asJsoup()
            .select("a[href*=book]")
            .map { it.toSManga() }
            .filter { manga ->
                query.isEmpty() || manga.title.contains(query.trim(), ignoreCase = true)
            }
        return MangasPage(mangas, false)
    }

    // Manga details
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SManga.create().apply {
            thumbnail_url = document.select("img.cover").attr("abs:src")
            description = document.select("div.detail_info1").text()
            title = document.select("div.title").text()
        }
    }

    // Chapter list
    private suspend fun fetchChapterList(manga: SManga): List<SChapter> = client.get(baseUrl + manga.url + "/get_chapter").parseAs<List<Dto>>().map { it.toSChapter() }

    // Page list
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url).asJsoup().select("img.lazy.comic_img").mapIndexed { i, el ->
        Page(i, imageUrl = el.attr("data-original"))
    }

    private fun org.jsoup.nodes.Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(attr("abs:href"))
        title = select(".container-title").text()
        thumbnail_url = select(".container-cover img").attr("abs:src")
    }
}
