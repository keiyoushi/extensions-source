package eu.kanade.tachiyomi.extension.en.xomanga

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

class XoManga : HttpSource() {
    override val baseUrl = "https://www.xomanga.site"
    override val lang = "en"
    override val supportsLatest = true
    override val name = "XoManga"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/our-works.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val block = EXCLUSIVE_REGEX.find(response.body.string())?.groupValues?.get(1) ?: return MangasPage(emptyList(), false)
        val exclusiveTitles = QUOTED_REGEX.findAll(block).map { it.groupValues[1].lowercase().trim().replace(Regex("""\s+"""), " ") }.toSet()
        val index = client.newCall(GET("$baseUrl/index.json", headers)).execute().parseAs<IndexResponse>()
        val mangas = index.latest.filter { it.isExclusive(exclusiveTitles) }.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/index.json", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<IndexResponse>()
        val mangas = result.latest.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/index.json".toHttpUrl().newBuilder()
            .fragment(query.trim())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment?.lowercase().orEmpty()
        val result = response.parseAs<IndexResponse>()
        val mangas = result.latest
            .filter { it.matchesQuery(query) }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/manga/${manga.url}/details.json", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/details.html?id=${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ChapterResponse>().chaptersList.map { it.toSChapter(baseUrl) }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val slug = url.pathSegments.first()
        val chapterNum = url.fragment
        val chapterUrl = "$baseUrl/reader.html".toHttpUrl().newBuilder()
            .addQueryParameter("id", slug)
            .addQueryParameter("ch", chapterNum)
            .build()
            .toString()
        return chapterUrl
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val slug = url.pathSegments.first()
        val chapterNum = url.fragment
        return GET("$baseUrl/manga/$slug/chapters/$chapterNum.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseAs<ImageResponse>().images
        return images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val EXCLUSIVE_REGEX = Regex("""myExclusiveWorksTitles\s*=\s*\[([^]]+)]""", RegexOption.DOT_MATCHES_ALL)
        private val QUOTED_REGEX = Regex("""["']([^"'\n]+)["']""")
    }
}
