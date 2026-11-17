package eu.kanade.tachiyomi.extension.th.whytoon

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class WhyToon : KeiSource() {

    override suspend fun getPopularManga(page: Int) = search(SearchQuery(sort = "popularity", page = page, limit = PAGE_SIZE))

    override suspend fun getLatestUpdates(page: Int) = search(SearchQuery(sort = "recent", page = page, limit = PAGE_SIZE))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val q = query.trim().ifEmpty { null }
        val searchQuery = SearchQuery(
            sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: if (q != null) "relevance" else "recent",
            page = page,
            limit = PAGE_SIZE,
            q = q,
            genre = filters.firstInstanceOrNull<GenreFilter>()?.value,
            completionStatus = filters.firstInstanceOrNull<StatusFilter>()?.value,
            countryOrigin = filters.firstInstanceOrNull<CountryFilter>()?.value,
            ageRating = filters.firstInstanceOrNull<AgeRatingFilter>()?.value,
        )
        return search(searchQuery)
    }

    private suspend fun search(query: SearchQuery): MangasPage {
        val url = "$baseUrl/api/trpc/content.search".toHttpUrl().newBuilder()
            .addQueryParameter("input", TrpcInput(query).toJsonString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<TrpcResponse<SearchDto>>().data
        return MangasPage(result.data.map { it.toSManga() }, result.hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val episodes = document.extractNextJs<EpisodeListDto>()!!
        return SMangaUpdate(document.toSManga(manga.url), episodes.toSChapters())
    }

    private fun Document.toSManga(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("h1")!!.text()
        thumbnail_url = selectFirst("img[src*=/_ndcovers/]")?.absUrl("src")
            ?.let { "$CDN_URL/content/${it.substringAfter("/content/").substringBefore('?')}" }
        genre = select("h1 + div a[href^=/browse/]").joinToString { it.text() }.ifEmpty { null }
        status = when (infoValue("สถานะ:")) {
            "กำลังดำเนินการ" -> SManga.ONGOING
            "จบแล้ว" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val rating = infoValue("คะแนน")?.let { RATING_REGEX.matchEntire(it) }?.groupValues?.takeIf { it[2] != "0" }
        description = MarkdownDescription.Builder()
            .rating(rating?.get(1)?.toDouble(), max = 5, votes = rating?.get(2)?.toInt())
            .field("เรตอายุ", infoValue("เรตอายุ:"))
            .description(selectFirst("h2:containsOwn(เรื่องย่อ) + div"))
            .build()
    }

    private fun Document.infoValue(label: String) = selectFirst("span:matchesOwn(^$label$)")?.nextElementSibling()?.text()

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val path = url.sitePath()
        if (path?.firstOrNull() != "browse") return super.getMangasByUrl(url, page)
        val q = url.queryParameter("q")?.ifBlank { null }
        val searchQuery = SearchQuery(
            sort = url.queryParameter("sort") ?: if (q != null) "relevance" else "recent",
            page = page,
            limit = PAGE_SIZE,
            q = q,
            genre = path.getOrNull(1)?.takeIf { it != "page" },
            completionStatus = url.queryParameter("completionStatus"),
            countryOrigin = url.queryParameter("countryOrigin"),
            ageRating = url.queryParameter("ageRating"),
        )
        return search(searchQuery)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.sitePath() ?: return null
        if (path.size < 2 || path[0] != "content") return null
        val manga = SManga.create().apply { this.url = "/content/${path[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun HttpUrl.sitePath() = pathSegments.filter(String::isNotEmpty)
        .takeIf { host.removePrefix("www.") == baseUrl.toHttpUrl().host }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith("🔒")) return emptyList()
        val reader = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .extractNextJs<ReaderDto> { it is JsonObject && "data" in it && "slug" in it && "no" in it }!!
        return reader.data.images.mapIndexed { i, path -> Page(i, imageUrl = "$CDN_URL/$path") }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        const val CDN_URL = "https://storage.whytoon.com"
        private const val PAGE_SIZE = 20
        private val RATING_REGEX = Regex("""([\d.]+) \((\d+)\)""")
    }
}
