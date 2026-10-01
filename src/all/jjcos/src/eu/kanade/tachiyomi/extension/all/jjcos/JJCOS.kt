package eu.kanade.tachiyomi.extension.all.jjcos

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.tryParseDateTime
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class JJCOS : KeiSource() {

    override val supportsLatest = false

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val posts = client.get(indexUrlBuilder(page = page).build()).parseAs<IndexDto>().posts

        return toMangasPage(posts, page)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val sourceHost = baseUrl.toHttpUrl().host
        if (url.host != sourceHost && url.host != "www.$sourceHost") {
            return null
        }

        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (pathSegments.firstOrNull() != "post") {
            return null
        }

        val manga = SManga.create().apply {
            this.url = normalizePath(url.encodedPath)
        }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val posts = client.get(indexUrlBuilder(page = page, query = query).build()).parseAs<IndexDto>().posts
        val trimmedQuery = query.trim()

        val filteredPosts = if (trimmedQuery.isEmpty()) {
            posts
        } else {
            val normalizedQuery = trimmedQuery.lowercase(Locale.ROOT)
            posts.filter { post ->
                post.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    post.content?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
            }
        }

        return toMangasPage(filteredPosts, page)
    }

    // ========================= Details & Chapters ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val postPath = response.request.url.encodedPath
        val document = response.asJsoup()

        manga.apply {
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

            url = postPath
        }

        val dateUpload = parseDate(
            document.selectFirst("meta[property=article:published_time]")?.attr("content")
                ?: document.selectFirst(".breadcrumb-item.date-overlay")?.text(),
        )

        val chapter = SChapter.create().apply {
            url = normalizePath(postPath)
            name = "Gallery"
            date_upload = dateUpload
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imageUrls = extractImageUrls(document)

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index = index, imageUrl = imageUrl)
        }
    }

    // ============================= Utilities =============================

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
        val startIndex = (page.coerceAtLeast(1) - 1) * PAGE_SIZE
        if (startIndex >= posts.size) {
            return MangasPage(emptyList(), false)
        }

        val endIndexExclusive = minOf(posts.size, startIndex + PAGE_SIZE)
        val mangas = posts.subList(startIndex, endIndexExclusive).map { post ->
            post.toSManga(linkToEncodedPath(post.link))
        }

        return MangasPage(
            mangas = mangas,
            hasNextPage = endIndexExclusive < posts.size,
        )
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

    private fun indexUrlBuilder(page: Int, query: String? = null): HttpUrl.Builder = "$baseUrl/api/index.html".toHttpUrl().newBuilder().apply {
        addQueryParameter("page", page.toString())
        if (!query.isNullOrBlank()) {
            addQueryParameter("query", query)
        }
    }

    private fun parseDate(rawDate: String?): Long = DATE_TIME_FORMAT.tryParseDateTime(rawDate).takeIf { it != 0L }
        ?: DATE_FORMAT.tryParseDate(rawDate)

    companion object {
        private const val PAGE_SIZE = 20

        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    }
}
