package eu.kanade.tachiyomi.extension.tr.kairatoon

import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.util.Locale

@Source
abstract class Kairatoon : KeiSource() {

    private val apiUrl get() = baseUrl.toHttpUrl().resolve("/api/")!!

    override suspend fun getPopularManga(page: Int) = getWebtoons(page, "popular")

    override suspend fun getLatestUpdates(page: Int) = getWebtoons(page, "newest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.checked.orEmpty()
        return getWebtoons(page, filters.firstInstanceOrNull<SortFilter>()?.selected ?: "popular") {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString(","))
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.selected?.let { addQueryParameter("type", it) }
        }
    }

    // The site asks for the list and for the total of the same filters, which tells where the list ends
    private suspend fun getWebtoons(page: Int, sort: String, filters: HttpUrl.Builder.() -> Unit = {}): MangasPage = coroutineScope {
        val total = async {
            client.get(apiUrl.newBuilder().addPathSegments("webtoons/count").apply(filters).build(), Fetch.XHR).parseAs<CountDto>().total
        }
        val url = apiUrl.newBuilder()
            .addPathSegment("webtoons")
            .apply(filters)
            .addQueryParameter("sort", sort)
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("page", page.toString())
            .build()
        val mangas = client.get(url, Fetch.XHR).parseAs<List<WebtoonDto>>()
            .filter { it.isComic }
            .map { it.toSManga(baseUrl.toHttpUrl()) }
        MangasPage(mangas, page * LIMIT < total.await())
    }

    // Catalogue and genre links; series and chapter links go to getMangaByUrl
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (!url.isSite()) return MangasPage(emptyList(), false)
        return when (segments.firstOrNull()) {
            "katalog" -> getWebtoons(page, url.queryParameter("sort") ?: "newest") {
                CATALOG_FILTERS.forEach { name -> url.queryParameter(name)?.let { addQueryParameter(name, it) } }
            }
            "tur" -> {
                val genre = getGenres().firstOrNull { it.name.toSlug() == segments.getOrNull(1) } ?: return MangasPage(emptyList(), false)
                getWebtoons(page, "newest") { addQueryParameter("genre", genre.name) }
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1)
        if (!url.isSite() || url.pathSegments[0] != "webtoon" || slug.isNullOrEmpty()) return null
        val manga = SManga.create().apply { this.url = "/webtoon/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val webtoonUrl = apiUrl.newBuilder().addPathSegment("webtoons").addPathSegment(manga.url.substringAfterLast('/')).build()

        return SMangaUpdate(
            manga = { if (fetchDetails) client.get(webtoonUrl, Fetch.XHR).parseAs<WebtoonDto>().toSManga(baseUrl.toHttpUrl()) else manga },
            chapters = {
                if (fetchChapters) {
                    client.get(webtoonUrl.newBuilder().addPathSegment("chapters").build(), Fetch.XHR).parseAs<List<ChapterDto>>()
                        .map { it.toSChapter(manga.url) }
                        .reversed()
                } else {
                    chapters
                }
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterSlug) = chapter.url.removePrefix("/webtoon/").split('/')
        val url = apiUrl.newBuilder()
            .addPathSegments("webtoons/$slug/read")
            .addPathSegment(chapterSlug.removePrefix("bolum-"))
            .build()
        return client.get(url, Fetch.XHR.from(getChapterUrl(chapter)), ensureSuccess = false).use { response ->
            when (response.code) {
                // Premium early access, and +18 series without an account logged in through WebView
                401 -> emptyList()
                else -> {
                    if (!response.isSuccessful) throw HttpException(response.code)
                    response.parseAs<ReadDto>().toPages(baseUrl.toHttpUrl())
                }
            }
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = getGenres().toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<GenreDto>>())

    private suspend fun getGenres() = client.get(apiUrl.resolve("genres")!!, Fetch.XHR).parseAs<List<GenreDto>>()

    // Genre pages are named after the genre in ASCII: "Geçmişe Dönme" is /tur/gecmise-donme
    private fun String.toSlug() = Normalizer.normalize(lowercase(TURKISH).replace('ı', 'i'), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(SPACES, "-")

    // The site moved from kairatoon.com, which redirects here
    private fun HttpUrl.isSite() = host.removePrefix("www.") in setOf(baseUrl.toHttpUrl().host, "kairatoon.com")

    companion object {
        private const val LIMIT = 24
        private val CATALOG_FILTERS = listOf("q", "genre", "status", "type")
        private val TURKISH = Locale.forLanguageTag("tr")
        private val MARKS = Regex("""\p{Mn}+""")
        private val SPACES = Regex("""\s+""")
    }
}
