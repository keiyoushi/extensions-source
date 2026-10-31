package eu.kanade.tachiyomi.extension.es.celestialmoon

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.text.Collator
import java.util.Locale

@Source
abstract class CelestialMoon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3) { it.host == baseHttpUrl.host }
        // Catalogue is served no-store
        addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful || chain.request().url.queryParameter("action") != "public") return@addNetworkInterceptor response
            response.newBuilder()
                .header("Cache-Control", "max-age=600")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }
    }

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    private val apiUrl get() = baseHttpUrl.resolve("/api/index.php")!!

    private suspend fun fetchCatalog(): StateDto = client.get(apiUrl.newBuilder().addQueryParameter("action", "public").build())
        .parseAs<CatalogDto>().state

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(0)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(1)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val catalog = fetchCatalog()
        val keyword = query.trim().lowercase()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val books = catalog.publishedBooks.filter { book ->
            (keyword.isEmpty() || (listOf(book.title) + book.genres).joinToString(" ").lowercase().contains(keyword)) &&
                (genre == null || genre in book.genres) &&
                (status == null || book.status == status)
        }
        val sorted = when (filters.firstInstanceOrNull<SortFilter>()?.state ?: 0) {
            1 -> books.associateWith { book -> catalog.chaptersOf(book).maxOfOrNull { it.date_upload } ?: 0L }
                .entries.sortedByDescending { it.value }.map { it.key }
            2 -> books.sortedWith(compareBy(Collator.getInstance(Locale.forLanguageTag("es"))) { it.title })
            3 -> books.sortedByDescending { it.periodViews?.day ?: 0 }
            4 -> books.sortedByDescending { it.views }
            else -> books.sortedByDescending { it.periodViews?.week ?: 0 }
        }
        return MangasPage(sorted.map { it.toSManga(baseHttpUrl) }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if ((url.host != baseHttpUrl.host && url.host !in OTHER_HOSTS) || url.pathSegments.size < 2 || url.pathSegments[0] !in listOf("obra", "leer")) return null
        val id = url.pathSegments[1]
        return fetchCatalog().publishedBooks.find { it.id == id }?.toSManga(baseHttpUrl)
    }

    // Entries saved by the MangaThemesia version store the old WordPress path, with the book id in memo
    private val SManga.bookId get() = if (url.startsWith("/")) memo.getStringOrNull("id") else url

    override fun getMangaUrl(manga: SManga): String = manga.bookId?.let { "$baseUrl/obra/$it" } ?: baseUrl

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val catalog = fetchCatalog()
        val bookId = manga.bookId
        val book = if (bookId != null) {
            catalog.publishedBooks.firstOrNull { it.id == bookId }
        } else {
            val slug = baseHttpUrl.resolve(manga.url)!!.pathSegments.lastOrNull(String::isNotEmpty) ?: throw Exception(MISSING_SERIES)
            catalog.publishedBooks.firstOrNull { it.sourceUrl?.toHttpUrlOrNull()?.pathSegments?.lastOrNull(String::isNotEmpty) == slug }
        } ?: throw Exception(MISSING_SERIES)
        val details = book.toSManga(baseHttpUrl).apply {
            url = manga.url
            memo = buildJsonObject { put("id", book.id) }
        }
        return SMangaUpdate(details, catalog.chaptersOf(book))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = baseHttpUrl.resolve(chapter.url)!!.pathSegments
        // Chapters saved by the MangaThemesia version
        if (segments.size != 3 || segments[0] != "leer") throw Exception("Refresh the chapter list")
        val (_, book, number) = segments
        val url = apiUrl.newBuilder()
            .addQueryParameter("action", "chapter")
            .addQueryParameter("book", book)
            .addQueryParameter("number", number)
            .build()
        return client.get(url).parseAs<ReaderDto>().chapter.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.src)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = fetchCatalog().publishedBooks.flatMap { it.genres }.distinct().sorted().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            data?.parseAs<List<String>>()?.let(::GenreFilter),
            StatusFilter(),
            Filter.Separator(),
            SortFilter(),
        ),
    )

    companion object {
        private const val MISSING_SERIES = "Series not found on the site, migrate this entry"

        // The site's earlier app host, and the reader host it sends adult chapters to
        private val OTHER_HOSTS = setOf(
            "palegoldenrod-mallard-636284.hostingersite.com",
            "mediumaquamarine-cheetah-476183.hostingersite.com",
        )
    }
}
