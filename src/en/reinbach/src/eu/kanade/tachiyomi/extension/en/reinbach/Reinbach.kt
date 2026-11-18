package eu.kanade.tachiyomi.extension.en.reinbach

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * reinbachart.com is a WordPress site.
 *
 * - A story is a top-level category ("This Romantic World").
 * - A chapter is a single marker post titled "Chapter <Word> - <Subtitle>" whose body is the
 *   chapter's title card.
 * - A page is a post holding exactly one full-size image, ordered by post date.
 *
 * Chapter membership is derived from post dates, not from the per-chapter categories: those are
 * flat siblings of the story category and are occasionally mis-tagged.
 *
 * URLs carry the story's category id as a query parameter (`/category/<slug>/?id=<id>`) so the
 * chapter and page queries can scope by `categories=<id>` without resolving the slug to an id on
 * every call. [getMangaUrl] and [getChapterUrl] strip it again.
 */
@Source
abstract class Reinbach : KeiSource() {

    private val coverCache = ConcurrentHashMap<Int, String>()

    // Keeps API calls polite without throttling the page images.
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { url ->
        url.host == baseUrl.toHttpUrl().host && !url.encodedPath.startsWith("/wp-content/")
    }

    // ============================== Listing ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val stories = client.get(categoriesUrl().build(), Fetch.XHR)
            .parseAs<List<WpCategory>>()
            .filter { it.isStory() }
            .map { it.toSManga(coverFor(it.id)) }

        val term = query.trim()
        if (term.isEmpty()) return MangasPage(stories, false)

        // Tapping a genre in the app turns into a search, so genre names are accepted too.
        return MangasPage(
            stories.filter { it.title.contains(term, ignoreCase = true) || GENRES.any { genre -> genre.contains(term, ignoreCase = true) } },
            false,
        )
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) mangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) chapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "category") return null
        val slug = segments[1].ifBlank { return null }

        val category = client.get(
            api("categories").addQueryParameter("slug", slug).build(),
            Fetch.XHR,
        ).parseAs<List<WpCategory>>().firstOrNull() ?: return null

        return category.toSManga(coverFor(category.id)).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.substringBefore('?')

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore('?')

    private suspend fun mangaDetails(manga: SManga): SManga {
        val categoryId = categoryId(manga.url) ?: return manga
        val category = client.get(api("categories/$categoryId").build(), Fetch.XHR).parseAs<WpCategory>()
        val categoryTitle = category.name.unescape()

        return SManga.create().apply {
            url = manga.url
            title = categoryTitle
            thumbnail_url = coverFor(categoryId)
            author = AUTHOR
            artist = AUTHOR
            status = SManga.ONGOING
            genre = GENRES.joinToString()
            description = descriptionFor(category.slug, categoryTitle)
            initialized = true
        }
    }

    // ============================== Chapters ==============================

    private suspend fun chapterList(manga: SManga): List<SChapter> {
        val categoryId = categoryId(manga.url) ?: return emptyList()
        val requestUrl = api("posts").apply {
            addQueryParameter("categories", categoryId.toString())
            addQueryParameter("search", "Chapter")
            addQueryParameter("per_page", PER_PAGE.toString())
            addQueryParameter("orderby", "date")
            addQueryParameter("order", "asc")
            addQueryParameter("_fields", "id,date,title,link")
        }.build()

        val markers = client.get(requestUrl, Fetch.XHR).parseAs<List<WpPost>>()
            .filter { CHAPTER_TITLE.containsMatchIn(it.title.rendered.unescape()) }
            .sortedBy { parseDate(it.date) }

        // Newest first: the app lists the returned order and walks the reader in reverse.
        return markers.mapIndexed { index, post ->
            SChapter.create().apply {
                name = post.title.rendered.unescape()
                url = "${post.link.toHttpUrl().encodedPath}?id=$categoryId"
                chapter_number = (index + 1).toFloat()
                date_upload = parseDate(post.date)
            }
        }.reversed()
    }

    // ============================== Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val categoryId = categoryId(chapter.url) ?: throw Exception("Invalid chapter URL")
        if (chapter.date_upload <= 0L) throw Exception("Unknown chapter date, refresh the chapter list and retry")

        val imageUrls = mutableListOf<String>()
        var page = 1
        var firstBatch = true
        var done = false

        while (!done && page <= MAX_PAGES) {
            val url = api("posts").apply {
                addQueryParameter("categories", categoryId.toString())
                addQueryParameter("after", formatWpDate(chapter.date_upload - 1_000L))
                addQueryParameter("per_page", PER_PAGE.toString())
                addQueryParameter("page", page.toString())
                addQueryParameter("orderby", "date")
                addQueryParameter("order", "asc")
                addQueryParameter("_fields", "id,title,content")
            }.build()

            val posts = client.get(url, Fetch.XHR).parseAs<List<WpPost>>()
            for ((index, post) in posts.withIndex()) {
                if (CHAPTER_TITLE.containsMatchIn(post.title.rendered.unescape())) {
                    // The first post of the window is this chapter's own title card.
                    if (firstBatch && index == 0) {
                        post.imageUrl()?.let(imageUrls::add)
                        continue
                    }
                    done = true
                    break
                }
                post.imageUrl()?.let(imageUrls::add)
            }

            firstBatch = false
            if (done || posts.size < PER_PAGE) break
            page++
        }

        if (imageUrls.isEmpty()) throw Exception("No pages found")

        return imageUrls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    // ============================== Helpers ==============================

    private fun api(route: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("wp-json/wp/v2/$route")

    private fun categoriesUrl(): HttpUrl.Builder = api("categories").apply {
        addQueryParameter("per_page", "100")
        addQueryParameter("orderby", "count")
        addQueryParameter("order", "desc")
        addQueryParameter("_fields", "id,name,slug,count,parent,link")
    }

    private fun categoryId(url: String): Int? = url.substringAfter("?id=", "").substringBefore('&').toIntOrNull()

    private fun WpCategory.isStory(): Boolean = parent == 0 &&
        count > 1 &&
        link.isNotBlank() &&
        !name.contains("uncategor", ignoreCase = true) &&
        !CHAPTER_CATEGORY.containsMatchIn(name)

    private fun WpCategory.toSManga(cover: String?): SManga = SManga.create().apply {
        title = name.unescape()
        url = "/category/$slug/?id=$id"
        thumbnail_url = cover
        genre = GENRES.joinToString()
    }

    /**
     * Cover art of a story: the image of its oldest post, which is the first chapter's title card.
     */
    private suspend fun coverFor(categoryId: Int): String? {
        coverCache[categoryId]?.let { return it }

        val url = api("posts").apply {
            addQueryParameter("categories", categoryId.toString())
            addQueryParameter("per_page", "1")
            addQueryParameter("orderby", "date")
            addQueryParameter("order", "asc")
            addQueryParameter("_fields", "content")
        }.build()

        val cover = client.get(url, Fetch.XHR).parseAs<List<WpPost>>()
            .firstOrNull()
            ?.imageUrl()

        if (cover != null) coverCache[categoryId] = cover
        return cover
    }

    private suspend fun descriptionFor(slug: String, title: String): String {
        if (slug == TRW_SLUG) scrapedDescription()?.let { return it }
        return "$title is an adult webcomic written and drawn by $AUTHOR, published page by page at $baseUrl."
    }

    /**
     * The story's blurb is the intro of the /characters/ page, up to the cast list.
     */
    private suspend fun scrapedDescription(): String? = runCatching {
        val url = api("pages").apply {
            addQueryParameter("slug", "characters")
            addQueryParameter("_fields", "content")
        }.build()

        val html = client.get(url, Fetch.XHR).parseAs<List<WpPost>>()
            .firstOrNull()
            ?.content
            ?.rendered
            ?: return@runCatching null

        Jsoup.parse(html)
            .select("p")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .takeWhile { !it.startsWith("Let", ignoreCase = true) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n")
    }.getOrNull()

    private fun WpPost.imageUrl(): String? {
        val tag = IMG_TAG.find(content.rendered)?.value ?: return null
        val raw = DATA_SRC.find(tag)?.groupValues?.get(1)
            ?: SRC.find(tag)?.groupValues?.get(1)
            ?: return null
        return raw.replace("&amp;", "&").replace("&#038;", "&").takeIf { it.startsWith("http") }
    }

    private fun String.unescape(): String = Jsoup.parse(this).text()

    private fun parseDate(raw: String): Long = runCatching {
        LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrDefault(0L)

    private fun formatWpDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDateTime()
        .toString()

    companion object {
        private const val AUTHOR = "Reinbach"
        private const val TRW_SLUG = "this-romantic-world"
        private const val PER_PAGE = 100
        private const val MAX_PAGES = 20

        private val GENRES = listOf("Adult", "Comedy", "Romance", "School Life", "Slice of Life")

        private val CHAPTER_TITLE = Regex("""^\s*chapter\b""", RegexOption.IGNORE_CASE)
        private val CHAPTER_CATEGORY = Regex("""\bchapter\b""", RegexOption.IGNORE_CASE)
        private val IMG_TAG = Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE)
        private val DATA_SRC = Regex("""data-src="([^"]+)"""", RegexOption.IGNORE_CASE)
        private val SRC = Regex("""\ssrc="([^"]+)"""", RegexOption.IGNORE_CASE)
    }
}
