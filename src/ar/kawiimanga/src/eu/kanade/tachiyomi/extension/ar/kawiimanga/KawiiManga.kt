package eu.kanade.tachiyomi.extension.ar.kawiimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class KawiiManga : HttpSource() {
    override val name = "Kawii Manga"
    override val lang = "ar"

    private val apiUrl = "https://manga-api.kawaii-anime.com/api/manga/own"

    override val baseUrl = "https://kawaiimanga.org"

    override val supportsLatest = true

    override val client = network.client

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("x-app-key", "km_2026_live")

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl?action=browse&page=$page&sort=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaList>()

        val entries = data.results.map { it.toSManga() }

        return MangasPage(entries, data.hasMore)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl?action=browse&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.size >= 2) {
                val slug = url.pathSegments[1]
                val manga = SManga.create().apply { this@apply.url = slug }
                return fetchMangaDetails(manga)
                    .map { MangasPage(listOf(it), false) }
            }

            throw Exception("Unsupported url")
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("action", "search")
            addQueryParameter("q", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl?action=series&slug=${manga.url}", headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Manga>().toSManga()

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Manga>()

        return data.chapters.map { it.toSChapter(data.slug) }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl?action=pages&chapterId=${chapter.url.substringAfterLast('#')}", headers)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url.substringBeforeLast("#")}"

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<Pages>()

        return data.pages.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
