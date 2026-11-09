package eu.kanade.tachiyomi.extension.en.manhwax

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
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ManhwaX : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(
        page,
        query = query.trim().takeIf(String::isNotEmpty),
        sort = filters.firstInstanceOrNull<SortFilter>()?.value,
        genre = filters.firstInstanceOrNull<GenreFilter>()?.slug,
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "webtoon") return null
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
            title = document.selectFirst("h1.wt-title")!!.text().removePrefix("18+ ")
            thumbnail_url = document.selectFirst(".wt-cover img")?.absUrl("src")
            description = MarkdownDescription.Builder()
                .description(document.selectFirst("p.wt-desc"))
                .build()
        }
        val chapterList = document.select(".wt-chapters a.wt-ch").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".wt-ch-t")!!.text()
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val (slug, chapterSlug) = chapterUrl.toHttpUrl().pathSegments.drop(1)
        val apiUrl = "$baseUrl/api/chapter".toHttpUrl().newBuilder()
            .addPathSegment(slug)
            .addPathSegment(chapterSlug)
            .build()
        return client.get(apiUrl, Fetch.XHR.from(chapterUrl)).parseAs<ChapterDto>().images.mapIndexed { index, image ->
            val imageUrl = "$baseUrl/api/img".toHttpUrl().newBuilder()
                .addQueryParameter("url", image)
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tags", Fetch.DOCUMENT).asJsoup()
        .select("a.tag-card")
        .map { GenreDto(it.selectFirst("span")!!.text(), it.absUrl("href").toHttpUrl().queryParameter("genre")!!) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>().orEmpty()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                GenreFilter(genres).takeIf { genres.isNotEmpty() },
            ),
        )
    }

    private suspend fun getMangaList(page: Int, query: String? = null, sort: String? = null, genre: String? = null): MangasPage {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
            query?.let { addQueryParameter("q", it) }
            genre?.let { addQueryParameter("genre", it) }
            sort?.let { addQueryParameter("sort", it) }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".hx-grid a.hx-card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst(".hx-title")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }
}
