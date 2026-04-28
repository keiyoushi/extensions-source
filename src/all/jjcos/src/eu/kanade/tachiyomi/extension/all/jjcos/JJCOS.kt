package eu.kanade.tachiyomi.extension.all.jjcos

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class JJCOS : HttpSource() {

    override val name = "JJCOS"

    override val baseUrl = BASE_URL

    override val lang = "all"

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()

    private val indexUrl = "$baseUrl/api/index.html".toHttpUrl()

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET(
        indexUrlBuilder(page = page).build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val posts = response.parseAs<IndexDto>().posts
        val page = response.request.url.queryParameter("page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        return toMangasPage(posts, page)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val mangaPath = parseDeeplinkToMangaPath(query)
            ?: return super.fetchSearchManga(page, query, filters)

        val manga = SManga.create().apply {
            url = mangaPath
        }

        return fetchMangaDetails(manga).map {
            MangasPage(
                mangas = listOf(it),
                hasNextPage = false,
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        indexUrlBuilder(page = page, query = query).build(),
        headers,
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val posts = response.parseAs<IndexDto>().posts
        val requestUrl = response.request.url
        val page = requestUrl.queryParameter("page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val query = requestUrl.queryParameter("query")?.trim().orEmpty()

        val filteredPosts = if (query.isEmpty()) {
            posts
        } else {
            val normalizedQuery = query.lowercase(Locale.ROOT)
            posts.filter { post ->
                post.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    post.content?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
            }
        }

        return toMangasPage(filteredPosts, page)
    }

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${normalizePath(manga.url)}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.fh5co-article-title")
                ?.text()
                ?.removeSuffix(" - JJCOS")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }!!

            thumbnail_url = document.selectFirst("#post-content img, article img")?.absUrl("src")

            genre = document.select(".tag-container a.tag")
                .map { it.text().removePrefix("#").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotEmpty() }

            status = SManga.COMPLETED

            url = response.request.url.encodedPath
        }
    }

    // ========================= Chapters =========================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapterToPostPath(chapter.url)}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val postPath = response.request.url.encodedPath
        val dateUpload = parseDate(
            document.selectFirst("meta[property=article:published_time]")?.attr("content")
                ?: document.selectFirst(".breadcrumb-item.date-overlay")?.text(),
        )

        return listOf(
            SChapter.create().apply {
                url = normalizePath(postPath)
                name = "Gallery"
                date_upload = dateUpload
            },
        )
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageUrls = extractImageUrls(document)

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index = index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun extractImageUrls(document: Document): List<String> {
        val imageUrls = linkedSetOf<String>()

        document.select("#post-content img, article #post-content img, article p img")
            .forEach { imageElement ->
                val url = imageElement.absUrl("src")
                    .ifBlank { imageElement.absUrl("data-src") }
                    .trim()

                if (url.isNotEmpty()) {
                    imageUrls += url
                }
            }

        return imageUrls.toList()
    }

    private fun toMangasPage(posts: List<PostDto>, page: Int): MangasPage {
        val startIndex = (page - 1) * PAGE_SIZE
        if (startIndex >= posts.size) {
            return MangasPage(emptyList(), false)
        }

        val endIndexExclusive = minOf(posts.size, startIndex + PAGE_SIZE)
        val mangas = posts.subList(startIndex, endIndexExclusive).map { post ->
            post.toSManga()
        }

        return MangasPage(
            mangas = mangas,
            hasNextPage = endIndexExclusive < posts.size,
        )
    }

    private fun PostDto.toSManga(): SManga {
        val mangaTitle = title.trim()
        if (mangaTitle.isEmpty()) {
            throw IOException("Missing title in $link")
        }

        return SManga.create().apply {
            title = mangaTitle
            thumbnail_url = feature
            status = SManga.COMPLETED
            url = linkToEncodedPath(link)
            initialized = true
        }
    }

    private fun parseDeeplinkToMangaPath(query: String): String? {
        val queryUrl = parseQueryAsHttpUrl(query) ?: return null
        val sourceHost = baseUrl.toHttpUrl().host
        val pathSegments = queryUrl.pathSegments.filter { it.isNotEmpty() }
        val encodedPath = queryUrl.encodedPath

        if (queryUrl.host != sourceHost && queryUrl.host != "www.$sourceHost") {
            return null
        }

        if (pathSegments.isEmpty()) {
            return null
        }

        return if (pathSegments.first() == "post") normalizePath(encodedPath) else null
    }

    private fun parseQueryAsHttpUrl(query: String): HttpUrl? {
        val trimmedQuery = query.trim()

        return trimmedQuery.toHttpUrlOrNull()
            ?: trimmedQuery.replace(" ", "%20").toHttpUrlOrNull()
            ?: when {
                trimmedQuery.startsWith("/post/") -> "$baseUrl${trimmedQuery.replace(" ", "%20")}".toHttpUrlOrNull()
                else -> null
            }
    }

    private fun chapterToPostPath(chapterPath: String): String {
        val rawPath = chapterPath.takeIf { it.startsWith("/post/") }
            ?: throw IOException("Invalid chapter path: $chapterPath")

        return normalizePath(rawPath)
    }

    private fun linkToEncodedPath(link: String): String {
        val sanitizedLink = link.trim().substringBefore('?').substringBefore('#')
        val absoluteLink = when {
            sanitizedLink.startsWith("http://") || sanitizedLink.startsWith("https://") -> sanitizedLink
            sanitizedLink.startsWith("/") -> "$baseUrl$sanitizedLink"
            else -> "$baseUrl/$sanitizedLink"
        }.replace(" ", "%20")

        val parsed = absoluteLink.toHttpUrlOrNull()
            ?: throw IOException("Invalid post link: $link")

        return normalizePath(parsed.encodedPath)
    }

    private fun normalizePath(path: String): String {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"

        val parsed = "$baseUrl$normalizedPath".toHttpUrlOrNull()
            ?: throw IOException("Invalid path: $path")

        return parsed.encodedPath
    }

    private fun indexUrlBuilder(page: Int, query: String? = null): HttpUrl.Builder = indexUrl.newBuilder().apply {
        addQueryParameter("page", page.toString())
        if (!query.isNullOrBlank()) {
            addQueryParameter("query", query)
        }
    }

    private fun parseDate(rawDate: String?): Long = DATE_FORMATS.firstNotNullOfOrNull { format ->
        format.tryParse(rawDate).takeIf { it != 0L }
    } ?: 0L

    companion object {
        const val BASE_URL = "https://jjcos.com"
        private const val PAGE_SIZE = 20

        private val DATE_FORMATS: List<SimpleDateFormat> by lazy {
            listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            )
        }
    }
}
