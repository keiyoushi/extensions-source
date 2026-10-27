package eu.kanade.tachiyomi.extension.en.manhuatop

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class ManhuaTop : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.encodedPath.startsWith("/api/") }

    // The image hosts answer 404 for some files when an Origin is sent
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("trending-week")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("latest_update")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val keyword = query.trim()
        if (keyword.length == 1) {
            throw Exception("Search query must be at least 2 characters")
        }
        val listing = baseUrl.toHttpUrl().newBuilder().apply {
            if (keyword.isNotEmpty()) {
                addPathSegment("search").addPathSegment(keyword).addPathSegment("")
                if (page > 1) addQueryParameter("page", page.toString())
            } else {
                addPathSegment("series").addPathSegment("")
                if (page > 1) addPathSegment("page").addPathSegment(page.toString()).addPathSegment("")
            }
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        }.build()

        val discovery = client.get(apiUrl("discovery", listing)).parseAs<DiscoveryDto>()
        return MangasPage(discovery.items.map { it.toSManga() }, discovery.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "manhua") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val path = baseUrl.toHttpUrl().newBuilder().addPathSegment("manhua").addPathSegment(slug).addPathSegment("").build().encodedPath

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = path },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    // Entries saved by the Madara version can store the WordPress post id, with the path in memo
    private fun mangaPath(manga: SManga): String = if (manga.url.startsWith("/")) manga.url else manga.memo["path"]!!.string

    override fun getMangaUrl(manga: SManga): String = baseUrl + mangaPath(manga)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) parseDetails(manga, client.get(getMangaUrl(manga)).asJsoup()) else manga }
        val chapterList = async { if (fetchChapters) fetchChapters(mangaPath(manga)) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun parseDetails(manga: SManga, document: Document): SManga = manga.apply {
        title = document.selectFirst("h1#manga-title")!!.text()
        thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
        author = document.metadata("Author").joinToString().ifEmpty { null }
        artist = document.metadata("Artist").joinToString().ifEmpty { null }
        genre = (document.metadata("Type") + document.select(".genre-chips a, .tags-chips a").eachText())
            .distinctBy(String::lowercase).joinToString().ifEmpty { null }
        status = when (document.selectFirst(".detail-stats .status-badge")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on hold" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val info = listOfNotNull(
            document.selectFirst(".detail-stats .card-rating")?.textOrNull()?.let { "★ $it" },
            document.selectFirst(".detail-stats span[title$=views]")?.textOrNull(),
            document.metadata("Release").firstOrNull(),
        ).joinToString(" · ").ifEmpty { null }
        val synopsis = document.selectFirst(".rich-content.synopsis")?.wholeText()
            ?.lines()?.joinToString("\n") { it.trim() }?.replace(BLANK_LINES, "\n\n")?.trim()?.ifEmpty { null }
        val altTitles = document.selectFirst("p.alternative-title")?.textOrNull()
            ?.split(" / ")?.joinToString("\n") { "- $it" }?.let { "**Alternative titles:**\n$it" }
        description = listOfNotNull(info, synopsis, altTitles).joinToString("\n\n").ifEmpty { null }
    }

    // "Updating" is the site's placeholder value
    private fun Document.metadata(name: String): List<String> = select(".metadata-list dt:matchesOwn(^$name$) + dd a").eachText()
        .filterNot { it == "Updating" }

    private suspend fun fetchChapters(mangaPath: String): List<SChapter> = coroutineScope {
        val first = fetchChapterPage(mangaPath, 1)
        val rest = (2..(first.total + CHAPTERS_PER_PAGE - 1) / CHAPTERS_PER_PAGE).map { async { fetchChapterPage(mangaPath, it) } }
        (listOf(first) + rest.awaitAll()).flatMap { it.items }
            // The site shows these as "Reader link unavailable"
            .filter { it.path != null }
            // The site lists some chapters twice
            .distinctBy { it.path }
            .map { it.toSChapter(mangaPath) }
    }

    private suspend fun fetchChapterPage(mangaPath: String, page: Int): ChaptersDto {
        val series = (baseUrl + mangaPath).toHttpUrl().newBuilder()
            .addQueryParameter("tab", "chapters")
            .addQueryParameter("chapter_page", page.toString())
            .build()
        return client.get(apiUrl("reader", series, "chapters")).parseAs<ChaptersDto>()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        // Chapters saved before the Madara 1.6 migration store the full path
        if (chapter.url.startsWith("/")) return baseUrl + chapter.url
        return "$baseUrl${chapter.memo[MANGA_PATH]!!.string}${chapter.url}/"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val images = buildList {
            var page = 1
            do {
                val content = chapterUrl.newBuilder().apply { if (page > 1) addQueryParameter("content_page", page.toString()) }.build()
                val response = client.get(apiUrl("reader", content, "content")).parseAs<ContentDto>()
                addAll(response.images)
                page++
            } while (response.hasMore)
        }

        return images.mapIndexed { index, image -> Page(index, imageUrl = image.url) }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select(".detail-recommendations li.manga-card, .related-manga li.manga-card")
        .map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h3 a")!!
                url = link.absUrl("href").toHttpUrl().encodedPath
                title = link.text()
                thumbnail_url = card.selectFirst(".cover-link img")?.absUrl("src")
            }
        }
        .distinctBy { it.url }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = buildList {
            var page = 1
            do {
                val url = "$baseUrl/api/genres".toHttpUrl().newBuilder().addQueryParameter("page", page.toString()).build()
                val response = client.get(url).parseAs<GenresDto>()
                addAll(response.terms)
                page++
            } while (response.hasMore)
        }

        // The site lists genres with zero titles
        return genres.filter { it.count > 0 }.map { it.name to it.id }.sortedBy { it.first.lowercase() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>().orEmpty()

        return FilterList(
            buildList {
                add(SortFilter())
                add(TypeFilter())
                add(StatusFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    // The API takes the url of the page it serves
    private fun apiUrl(endpoint: String, page: HttpUrl, resource: String? = null): HttpUrl = "$baseUrl/api/$endpoint".toHttpUrl().newBuilder().apply {
        resource?.let { addQueryParameter("resource", it) }
        addQueryParameter("path", page.encodedPath + page.encodedQuery?.let { "?$it" }.orEmpty())
    }.build()

    companion object {
        private const val CHAPTERS_PER_PAGE = 30
        private val BLANK_LINES = Regex("""\n{3,}""")
    }
}
