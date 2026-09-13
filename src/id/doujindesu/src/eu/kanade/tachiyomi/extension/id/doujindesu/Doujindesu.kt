package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.LinkedHashMap

@Source
abstract class Doujindesu : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    private val decryptor = Decryptor { apiUrl }

    private val slugCache = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 30
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(decryptor.xorInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != baseUrl.toHttpUrl().host) {
                val headers = request.headers.newBuilder().removeAll("x-app-secret").build()
                chain.proceed(request.newBuilder().headers(headers).build())
            } else {
                chain.proceed(request)
            }
        }
    }

    override fun Headers.Builder.configureHeaders() = apply {
        add("x-app-secret", APP_SECRET)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(mangaListUrl(page, "rating"), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(mangaListUrl(page), page)

    private fun mangaListUrl(page: Int, sort: String = "latest_chapter"): HttpUrl {
        val offset = (page - 1) * LIMIT
        return "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort", sort)
            .build()
    }

    private suspend fun fetchMangaList(url: HttpUrl, page: Int): MangasPage {
        val response = client.get(url)
        val total = response.headers["x-total-count"]?.toIntOrNull()
        val hasNextPage = total?.let { page * LIMIT < it } ?: true
        val mangas = response.parseAs<List<MangaItem>>()
        return MangasPage(mangas.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    private suspend fun fetchTaxonomyList(url: HttpUrl): MangasPage {
        val dto = client.get(url).parseAs<TaxonomyMangas>()
        return MangasPage(
            dto.mangaList.map { it.toSManga(baseUrl) },
            dto.pagination.page < dto.pagination.totalPages,
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // priority when query exists: usual filter > type filter, otherwise opposite
        val hasQuery = query.isNotBlank()
        val agsFilter = filters.firstInstanceOrNull<AuthorGroupSeriesFilter>()
        val agsValue = filters.firstInstanceOrNull<AuthorGroupSeriesValueFilter>()?.state?.trim()

        if (!hasQuery && agsFilter != null && agsFilter.state in agsFilter.values.indices) {
            val selected = agsFilter.values[agsFilter.state]
            val type = selected.key
            val cacheKey = "$type:$agsValue"

            if (type.isNotBlank() && !agsValue.isNullOrBlank()) {
                // Search the input and pick a slug if not cached
                val slug = slugCache[cacheKey] ?: run {
                    val url = "$apiUrl/taxonomy/$type/".toHttpUrl().newBuilder()
                        .addQueryParameter("search", agsValue)
                        .addQueryParameter("limit", "1")
                        .build()
                    client.get(url).parseAs<TermsResult>().terms.firstOrNull()?.slug
                        ?: throw IOException("Gagal menemukan: $agsValue")
                }.also { slugCache[cacheKey] = it }

                val taxonomyUrl = "$apiUrl/taxonomy/$type/$slug".toHttpUrl().newBuilder()
                    .addQueryParameter("limit", LIMIT.toString())
                    .addQueryParameter("page", page.toString())
                    .build()
                return fetchTaxonomyList(taxonomyUrl)
            } else if (type.isBlank() && !agsValue.isNullOrBlank()) {
                throw IOException("Pilih tipe filter")
            }
        }

        // Usual query search + other filters
        val builder = mangaListUrl(page).newBuilder()

        if (hasQuery) builder.addQueryParameter("search", query)

        filters.forEach { filter ->
            when (filter) {
                is StatusList -> {
                    if (filter.state in filter.values.indices) {
                        filter.values[filter.state].key.takeIf { it.isNotBlank() }
                            ?.let { builder.addQueryParameter("status", it) }
                    }
                }
                is CategoryNames -> {
                    if (filter.state in filter.values.indices) {
                        filter.values[filter.state].key.takeIf { it.isNotBlank() }
                            ?.let { builder.addQueryParameter("type", it) }
                    }
                }
                is OrderBy -> {
                    if (filter.state in filter.values.indices) {
                        filter.values[filter.state].key.takeIf { it.isNotBlank() }
                            ?.let { builder.addQueryParameter("sort", it) }
                    }
                }
                is GenreList -> {
                    val selected = filter.state.filter { it.state }
                    if (selected.isNotEmpty()) {
                        builder.addEncodedQueryParameter("genre", selected.joinToString(",") { it.id.lowercase().replace(" ", "-") })
                    }
                }
                else -> {}
            }
        }

        return fetchMangaList(builder.build(), page)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.getSlug()}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = when (url.pathSegments.firstOrNull()) {
            "manga" -> url.pathSegments.getOrNull(1)
            "reader" -> {
                val chapterId = url.pathSegments.getOrNull(1) ?: return null
                client.get("$apiUrl/chapters/$chapterId".toHttpUrl()).parseAs<PageList>().mangaSlug
            }
            else -> return null
        } ?: return null
        return client.get("$apiUrl/manga/$slug".toHttpUrl()).parseAs<MangaItem>().toSManga(baseUrl).apply {
            setUrlWithoutDomain(getMangaUrl(this))
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val item = client.get("$apiUrl/manga/${manga.getSlug()}".toHttpUrl()).parseAs<MangaItem>()
        return SMangaUpdate(
            item.toSManga(baseUrl),
            item.chapters.mapIndexed { index, chapter ->
                chapter.toSChapter(isLast = item.isCompleted() && index == 0)
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/chapters/${chapter.url}".toHttpUrl()).parseAs<PageList>().pages.mapIndexed { i, imgUrl ->
        Page(i, imageUrl = Parser.unescapeEntities(imgUrl, false))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filter Tipe Diabaikan Saat Menggunakan Pencarian"),
        AuthorGroupSeriesFilter(authorGroupSeriesOptions),
        AuthorGroupSeriesValueFilter(),
        Filter.Separator(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(getGenreList()),
    )

    private fun SManga.getSlug(): String {
        val fullUrl = if (url.startsWith("http")) url else "$baseUrl/${url.removePrefix("/")}"
        return fullUrl.toHttpUrl().pathSegments.last { it.isNotBlank() }
    }

    companion object {
        private const val APP_SECRET = "dfdf72051dbfdc7d76889ebd31324e74"
        private const val LIMIT = 24
    }
}
