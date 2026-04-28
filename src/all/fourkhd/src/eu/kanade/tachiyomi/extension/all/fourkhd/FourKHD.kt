package eu.kanade.tachiyomi.extension.all.fourkhd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class FourKHD : HttpSource() {
    override val name = "4KHD"
    override val lang = "all"
    override val supportsLatest = true
    override val baseUrl = "https://www.4khd.com"

    private val postsApi = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl()
    private val pageSize = 20

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET(
        postsApi.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("_embed", "1")
            .addQueryParameter("orderby", "modified")
            .build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage = postsToMangaPage(response)

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET(
        postsApi.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("_embed", "1")
            .addQueryParameter("orderby", "date")
            .build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = postsToMangaPage(response)

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        deeplinkSlugFromQuery(query)?.let { slug ->
            return GET(
                postsApi.newBuilder()
                    .addQueryParameter("slug", slug)
                    .addQueryParameter("_embed", "1")
                    .build(),
                headers,
            )
        }

        val builder = postsApi.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", pageSize.toString())
            .addQueryParameter("_embed", "1")
            .addQueryParameter("orderby", "date")

        if (query.isNotBlank()) {
            builder.addQueryParameter("search", query)
        }

        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = postsToMangaPage(response)

    private fun postsToMangaPage(response: Response): MangasPage {
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull() ?: currentPage

        val posts = parsePosts(response)
        val mangas = posts.mapNotNull { it.toSManga() }

        return MangasPage(mangas, currentPage < totalPages)
    }

    // ========================= Details =========================

    override fun mangaDetailsRequest(manga: SManga): Request = postByPathRequest(manga.url)

    override fun getMangaUrl(manga: SManga): String = frontendPostUrl(manga.url)

    override fun mangaDetailsParse(response: Response): SManga {
        val post = parseSinglePost(response) ?: throw Exception("Missing post details from API")
        val id = post.id
        val title = post.titleText() ?: throw Exception("Missing title for post id=$id")

        return SManga.create().apply {
            val path = post.linkPath()
            this.title = title
            genre = post.genreText()
            thumbnail_url = post.thumbnailUrl()
            status = SManga.COMPLETED
            setUrlWithoutDomain(path.appendPostId(id))
        }
    }

    // ========================= Chapters =========================

    override fun chapterListRequest(manga: SManga): Request = postByPathRequest(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = frontendPostUrl(chapter.url)

    override fun chapterListParse(response: Response): List<SChapter> {
        val post = parseSinglePost(response) ?: return emptyList()

        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(post.linkPath().appendPostId(post.id))
                chapter_number = 1f
                name = "Gallery"
                date_upload = parseDate(post.date)
            },
        )
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = postByPathRequest(chapter.url)

    override fun pageListParse(response: Response): List<Page> {
        val apiImageUrls = parseSinglePost(response)
            ?.contentRendered()
            ?.let(::extractImageUrlsFromHtml)
            .orEmpty()

        return apiImageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun postByPathRequest(path: String): Request {
        val fullUrl = (baseUrl + path).toHttpUrl()
        val postId = fullUrl.queryParameter(POST_ID_QUERY)?.toIntOrNull()
        val slug = slugFromPath(fullUrl.encodedPath)

        val builder = if (postId != null) {
            postsApi.newBuilder().addPathSegment(postId.toString())
        } else {
            postsApi.newBuilder().apply {
                if (slug.isNotBlank()) {
                    addQueryParameter("slug", slug)
                } else {
                    val fallbackQuery = fullUrl.encodedPath.trim('/').substringAfterLast('/').ifBlank { fullUrl.encodedPath }
                    if (fallbackQuery.isNotBlank()) {
                        addQueryParameter("search", fallbackQuery)
                    }
                }
            }
        }.addQueryParameter("_embed", "1")

        return GET(
            builder.build(),
            headers,
        )
    }

    private fun slugFromPath(path: String): String {
        SLUG_PATH_REGEX.find(path)?.groupValues?.getOrNull(1)?.let {
            if (it.isNotBlank()) return it
        }

        val lastSegment = path.trimEnd('/').substringAfterLast('/')
        return lastSegment.substringBefore(".html")
    }

    private fun parsePosts(response: Response): List<PostDto> = response.use { res ->
        val element = res.parseAs<JsonElement>()
        if (bodyString(element).startsWith("[")) {
            element.parseAs<List<PostDto>>()
        } else {
            listOf(element.parseAs<PostDto>())
        }
    }

    private fun bodyString(element: JsonElement): String = element.toString().trimStart()

    private fun parseSinglePost(response: Response): PostDto? = parsePosts(response).firstOrNull()

    private fun PostDto.toSManga(): SManga? {
        val path = linkPath().takeIf { it != "/" } ?: return null
        val id = id
        val title = titleText() ?: throw Exception("Missing title for post id=$id")

        return SManga.create().apply {
            val pathWithId = path.appendPostId(id)
            this.title = title
            thumbnail_url = thumbnailUrl()
            genre = genreText()
            status = SManga.COMPLETED
            setUrlWithoutDomain(pathWithId)
            initialized = true
        }
    }

    private fun PostDto.titleText(): String? = title.rendered.htmlToText()

    private fun PostDto.contentRendered(): String = content.rendered

    private fun PostDto.linkPath(): String = link
        .toHttpUrlOrNull()
        ?.encodedPath
        .orEmpty()
        .ifBlank { "/" }

    private fun PostDto.thumbnailUrl(): String? = jetpackFeaturedMediaUrl?.let { normalizeImageUrl(it, forThumbnail = true) }
        ?: embedded?.featuredMedia?.firstOrNull()?.sourceUrl?.let { normalizeImageUrl(it, forThumbnail = true) }
        ?: extractImageUrlsFromHtml(content.rendered).firstOrNull()

    private fun PostDto.genreText(): String? {
        val names = embedded?.terms?.flatten()?.map { it.name }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
        return names.joinToString(", ").ifBlank { null }
    }

    private fun frontendPostUrl(path: String): String {
        val fullUrl = (baseUrl + path).toHttpUrl()
        val normalizedPath = fullUrl.encodedPath
        if (CONTENT_PATH_REGEX.containsMatchIn(normalizedPath)) {
            return "$baseUrl${normalizedPath.substringBefore('?')}"
        }

        fullUrl.queryParameter(POST_ID_QUERY)?.let { postId ->
            return baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("p", postId)
                .build()
                .toString()
        }

        return "$baseUrl/${normalizedPath.trimStart('/')}"
    }

    private fun deeplinkSlugFromQuery(query: String): String? {
        if (query.isBlank()) return null

        val url = query.toHttpUrlOrNull() ?: return null
        if (url.host.lowercase(Locale.ENGLISH) !in DEEPLINK_HOSTS) return null

        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.size < 3 || segments.first() != "content") return null

        val slugSegment = when {
            segments.last().all(Char::isDigit) && segments.size >= 4 -> segments[segments.lastIndex - 1]
            else -> segments.last()
        }

        if (!slugSegment.endsWith(".html")) return null
        return slugSegment.substringBefore(".html").takeIf { it.isNotBlank() }
    }

    private fun String.appendPostId(id: Int): String {
        val url = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(this)
            .addQueryParameter(POST_ID_QUERY, id.toString())
            .build()
        return url.toString().removePrefix(baseUrl)
    }

    private fun extractImageUrlsFromHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parseBodyFragment(html, baseUrl)
        val contentRoot = document.selectFirst(".entry-content.wp-block-post-content, .entry-content") ?: document.body()
        return extractImageUrlsFromElement(contentRoot)
    }

    private fun extractImageUrlsFromElement(root: Element): List<String> {
        val imageSources = root.select("img[data-src], img[data-lazy-src], img[src]")
            .mapNotNull { imageElement ->
                sequenceOf("abs:data-src", "abs:data-lazy-src", "abs:src")
                    .map { key -> imageElement.attr(key).trim() }
                    .firstOrNull { isImageUrl(it) }
            }
            .map { normalizeImageUrl(it, forThumbnail = false) }
            .filter { it.isNotBlank() }

        if (imageSources.isNotEmpty()) {
            return imageSources.distinctBy(::imageCanonicalKey)
        }

        val anchorSources = root.select("a[href]")
            .map { it.attr("abs:href").trim() }
            .filter(::isImageUrl)
            .map { normalizeImageUrl(it, forThumbnail = false) }
            .filter { it.isNotBlank() }

        return anchorSources.distinctBy(::imageCanonicalKey)
    }

    private fun imageCanonicalKey(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder().query(null).build().toString()
    }

    private fun normalizeImageUrl(url: String, forThumbnail: Boolean = false): String {
        val unescaped = url
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .trim()

        val parsed = unescaped.toHttpUrlOrNull() ?: return unescaped
        val host = parsed.host.lowercase(Locale.ENGLISH)

        val isJetpackProxy = host.startsWith("i") && host.endsWith(".wp.com")
        val rawPath = parsed.encodedPath.removePrefix("/")

        if (!isJetpackProxy || !rawPath.startsWith("pic.4khd.com/")) {
            return unescaped
        }

        val targetHost = if (forThumbnail) THUMBNAIL_CDN_HOST else PAGE_CDN_HOST
        val mappedPath = rawPath.removePrefix("pic.4khd.com/")
        val queryPart = parsed.encodedQuery?.let { "?$it" }.orEmpty()

        return "https://$targetHost/$mappedPath$queryPart"
    }

    private fun String.htmlToText(): String = Jsoup.parse(this).text().trim()

    private fun isImageUrl(url: String): Boolean = IMAGE_URL_REGEX.containsMatchIn(url)

    private fun parseDate(dateString: String?): Long = try {
        DATE_FORMAT.parse(dateString.orEmpty())?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        private val IMAGE_URL_REGEX = Regex("\\.(?:jpe?g|png|webp|gif|avif)(?:$|\\?)", RegexOption.IGNORE_CASE)
        private val SLUG_PATH_REGEX = Regex("/([^/]+)\\.html(?:/\\d+)?/?$")
        private val CONTENT_PATH_REGEX = Regex("^/content/", RegexOption.IGNORE_CASE)
        private val DEEPLINK_HOSTS = setOf("zgmz.uuss.uk", "4khd.com")
        private const val THUMBNAIL_CDN_HOST = "img.4khd.com"
        private const val PAGE_CDN_HOST = "img.uuss.uk"
        private const val POST_ID_QUERY = "post_id"
    }
}
