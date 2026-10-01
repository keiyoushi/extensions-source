package eu.kanade.tachiyomi.extension.zh.bh3

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
abstract class BH3 : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = connectTimeout(1.minutes)
        .readTimeout(1.minutes)
        .retryOnConnectionFailure(true)
        .followRedirects(true)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/book").asJsoup().select("a[href*=book]").map { element ->
            SManga.create().apply {
                url = "/book/${element.selectFirst("div.container")?.attr("id").orEmpty()}"
                title = element.selectFirst("div.container-title")?.text().orEmpty()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = throw Exception("No search")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga)).asJsoup()
            manga.apply {
                thumbnail_url = document.selectFirst("img.cover")?.attr("abs:src")
                description = document.selectFirst("div.detail_info1")?.text()
                title = document.selectFirst("div.title")?.text().orEmpty()
            }
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get(baseUrl + manga.url + "/get_chapter").parseAs<List<Dto>>().map { it.toSChapter() }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select("img.lazy.comic_img").mapIndexed { i, el ->
        Page(i, imageUrl = el.attr("data-original"))
    }
}
