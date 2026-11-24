package eu.kanade.tachiyomi.extension.ar.mangaar

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangaAR : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    // Like the site's popular page: the most read series of the week, then the trending ones
    override suspend fun getPopularManga(page: Int): MangasPage {
        val data = client.get("$apiUrl/public/trending", Fetch.XHR).parseAs<TrendingDto>()
        val mangas = (data.popular + data.trending).map { it.toSManga(baseUrl.toHttpUrl()) }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val data = client.get("$apiUrl/public/recent-chapters?limit=30&mode=diverse", Fetch.XHR).parseAs<RecentChaptersDto>()
        return MangasPage(data.chapters.map { it.toSManga(baseUrl.toHttpUrl()) }.distinctBy { it.url }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = mangaListUrl(page).apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.firstInstanceOrNull<TypeFilter>()?.value?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.value?.let { addQueryParameter("genre", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.value?.let { addQueryParameter("status", it) }
        }.build()
        return getMangaList(url)
    }

    // Series links are /manga/<id>-<slug> (often an Arabic slug) or /manga/<id>, chapter links are below them
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteUrl(url) || url.pathSegments.firstOrNull() != "manga") return null
        val id = url.pathSegments.getOrNull(1)?.substringBefore('-')?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        return getMangaUpdate(SManga.create().apply { this.url = id }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!isSiteUrl(url) || url.pathSegments.firstOrNull() != "category") return super.getMangasByUrl(url, page)
        val genre = CATEGORIES[url.pathSegments.getOrNull(1)] ?: return MangasPage(emptyList(), false)
        return getMangaList(mangaListUrl(page).addQueryParameter("genre", genre).build())
    }

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private fun mangaListUrl(page: Int) = "$apiUrl/public/manga".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("page_size", "36")

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val data = client.get(url, Fetch.XHR).parseAs<MangaListDto>()
        return MangasPage(data.manga.map { it.toSManga(baseUrl.toHttpUrl()) }, data.hasMore)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) client.get("$apiUrl/public/manga/${manga.url}", Fetch.XHR).parseAs<MangaDetailsDto>().toSManga(baseUrl.toHttpUrl()) else manga },
        chapters = { if (fetchChapters) client.get("$apiUrl/public/manga/${manga.url}/chapters", Fetch.XHR).parseAs<ChapterListDto>().toSChapterList(manga.url) else chapters },
    )

    // Chapters from MangaDex come through the site's image proxy, its own translations from its server
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangadexId = chapter.memo["mangadex"]?.string
        return if (mangadexId != null) {
            client.get("$apiUrl/public/chapter-by-id/$mangadexId/pages", Fetch.XHR).parseAs<ProxyPagesDto>().toPageList(baseUrl.toHttpUrl())
        } else {
            val mangaId = chapter.memo["manga"]!!.string
            val number = chapter.memo["number"]!!.string
            client.get("$apiUrl/chapters/by-manga/$mangaId/$number/pages", Fetch.XHR).parseAs<LocalPagesDto>().toPageList(baseUrl.toHttpUrl())
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/public/categories", Fetch.XHR).parseAs<CategoriesDto>().genres().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<String>>())

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.memo["manga"]!!.string}/chapter/${chapter.memo["number"]!!.string}"

    companion object {
        // The site's /category/<slug> pages, for its main genres
        private val CATEGORIES = mapOf(
            "action" to "أكشن",
            "adventure" to "مغامرة",
            "comedy" to "كوميديا",
            "drama" to "دراما",
            "fantasy" to "فانتازيا",
            "romance" to "رومانسي",
            "horror" to "رعب",
            "mystery" to "غموض",
            "martial-arts" to "فنون قتالية",
            "school-life" to "حياة مدرسية",
            "supernatural" to "خارق للطبيعة",
            "historical" to "تاريخي",
            "sports" to "رياضة",
            "thriller" to "إثارة",
            "isekai" to "إيسيكاي",
            "reincarnation" to "تناسخ",
            "magic" to "سحر",
            "slice-of-life" to "شريحة من الحياة",
            "sci-fi" to "خيال علمي",
            "psychological" to "نفسي",
        )
    }
}
