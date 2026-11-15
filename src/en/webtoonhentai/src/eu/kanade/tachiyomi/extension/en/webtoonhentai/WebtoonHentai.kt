package eu.kanade.tachiyomi.extension.en.webtoonhentai

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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class WebtoonHentai : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return getMangaList("search", page, query.trim())
        val tag = filters.firstInstanceOrNull<TagFilter>()?.slug ?: return getPopularManga(page)
        return getMangaList("tags/$tag", page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() !in URL_TYPES) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/webtoon/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = SManga.create().apply {
            url = manga.url
            // Series pages put "18+ " before every title
            title = document.selectFirst(".series-info h1")!!.text().removePrefix("18+ ")
            thumbnail_url = document.selectFirst(".series-cover img")?.absUrl("src")
            description = MarkdownDescription.Builder()
                .description(document.selectFirst(".series-info .desc p"))
                .build()
        }
        val chapterList = document.select(".chap-grid a.chap").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                val label = element.selectFirst(".name")!!.text()
                // The first and latest chapters are listed as "Read First" and "Read Last"
                name = if (label in BUTTON_NAMES) {
                    url.substringAfterLast('/').replace('-', ' ').replaceFirstChar(Char::uppercase)
                } else {
                    label
                }
            }
        }.distinctBy { it.url }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val (slug, chapterSlug) = chapterUrl.toHttpUrl().pathSegments.drop(1)
        val apiUrl = "$baseUrl/api-proxy/v1/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("url", "$UPSTREAM_URL/webtoon/$slug/$chapterSlug")
            .build()
        return client.get(apiUrl, Fetch.XHR.from(chapterUrl)).parseAs<ChapterDto>().pages.mapIndexed { index, image ->
            val imageUrl = "$baseUrl/api-proxy/v1/imgproxy".toHttpUrl().newBuilder()
                .addQueryParameter("url", image)
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private suspend fun getMangaList(path: String, page: Int, query: String? = null): MangasPage {
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            query?.let { addQueryParameter("q", it) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".grid a.card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst(".title")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    companion object {
        // The reader asks the site's proxy for the Manhwa18.cc chapter it copies
        private const val UPSTREAM_URL = "https://manhwa18.cc"
        private val URL_TYPES = setOf("webtoon", "read")
        private val BUTTON_NAMES = setOf("Read First", "Read Last")
    }
}
