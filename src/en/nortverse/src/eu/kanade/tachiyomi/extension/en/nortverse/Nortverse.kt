package eu.kanade.tachiyomi.extension.en.nortverse

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

@Source
abstract class Nortverse : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    private fun comic() = SManga.create().apply {
        url = "/comic/"
        title = "Nortverse"
        author = "nortuet"
        artist = "nortuet"
        description = "Tara and Beverly"
        status = SManga.ONGOING
        thumbnail_url = "https://i0.wp.com/nortverse.com/wp-content/uploads/2023/02/cropped-Site-Icon.png?fit=512%2C512&ssl=1"
    }

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(comic()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = listOf(comic()).filter { manga ->
            listOfNotNull(manga.title, manga.author, manga.description).any { it.contains(query, ignoreCase = true) }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "comic") return null
        return comic()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(comic(), chapters)

        val firstPage = client.get(comicListUrl(1))
        val totalPages = firstPage.header("X-WP-TotalPages")!!.toInt()
        val firstStrips = firstPage.parseAs<List<ComicDto>>()
        val strips = coroutineScope {
            val otherPages = (2..totalPages).map { page ->
                async { client.get(comicListUrl(page)).parseAs<List<ComicDto>>() }
            }
            firstStrips + otherPages.awaitAll().flatten()
        }
        // Numbered like the site's homepage list, #1 being the oldest strip
        val chapterList = strips.mapIndexed { index, strip -> strip.toSChapter(index + 1) }.reversed()
        return SMangaUpdate(comic(), chapterList)
    }

    private fun comicListUrl(page: Int) = "$baseUrl/wp-json/wp/v2/comic".toHttpUrl().newBuilder()
        .addQueryParameter("order", "asc")
        .addQueryParameter("per_page", "100")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("_fields", "slug,title,date_gmt")
        .build()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comic/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/wp-json/wp/v2/comic".toHttpUrl().newBuilder()
            .addQueryParameter("slug", chapter.url)
            .addQueryParameter("_fields", "content")
            .build()
        val content = client.get(url).parseAs<List<ContentDto>>().first().content.rendered
        return Jsoup.parseBodyFragment(content, baseUrl).select("img").mapIndexed { index, img ->
            // Full-size upload rather than the Jetpack-resized copy the page embeds
            val imageUrl = img.absUrl("src").toHttpUrl().newBuilder()
                .removeAllQueryParameters("resize")
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }
}
