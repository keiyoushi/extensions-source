package eu.kanade.tachiyomi.extension.en.kemonocafe

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.textOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class KemonoCafe : KeiSource() {

    override val supportsLatest = false

    private val siteHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { !it.encodedPath.startsWith("/wp-content/") }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchComics(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val comics = fetchComics().filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(comics, false)
    }

    // Only the comic subdomains carry the comic menu, so it is read from the first comic the front page links
    private suspend fun fetchComics(): List<SManga> {
        val home = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val comicUrl = home.select("section.comic_list a[href]")
            .first { it.absUrl("href").toHttpUrl().host.endsWith(".$siteHost") }
            .absUrl("href")
        val document = client.get(comicUrl, Fetch.DOCUMENT).asJsoup()
        return document.select("nav.menu-content a.menu-comic").map { it.toSManga() }
    }

    private fun Element.toSManga(): SManga = SManga.create().apply {
        url = storedUrl(absUrl("href").toHttpUrl())
        title = selectFirst(".menu-comic-title")!!.text()
        author = selectFirst(".menu-comic-author")?.textOrNull()
        artist = author
        // The menu groups comics under a genre heading, and retired ones under "Archived"
        genre = listOfNotNull(
            parent()!!.previousElementSibling()?.text()?.takeUnless { it == "Archived" },
            selectFirst(".adult-chip")?.text(),
        ).joinToString().ifEmpty { null }
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.endsWith(".$siteHost")) return null
        val comicHome = url.resolve("/")!!
        val document = client.get(archiveUrl(comicHome), Fetch.DOCUMENT).asJsoup()
        return document.parseDetails(comicHome)?.apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = comicUrl(manga.url).toString()

    override fun getChapterUrl(chapter: SChapter): String = comicUrl(chapter.url).toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comicHome = comicUrl(manga.url)
        val document = client.get(archiveUrl(comicHome), Fetch.DOCUMENT).asJsoup()
        // A comic dropped from the menu keeps its stored details
        val details = document.parseDetails(comicHome) ?: manga
        return SMangaUpdate(details, document.parseChapters(comicHome))
    }

    private fun Document.parseDetails(comicHome: HttpUrl): SManga? {
        val entry = select("nav.menu-content a.menu-comic")
            .firstOrNull { it.absUrl("href").toHttpUrl().host == comicHome.host }
            ?: return null
        return entry.toSManga().apply {
            description = selectFirst("meta[name=description]")?.attrOrNull("content")
        }
    }

    private fun Document.parseChapters(comicHome: HttpUrl): List<SChapter> {
        val chapters = archiveChapters().map { chapter ->
            SChapter.create().apply {
                url = storedUrl(chapter.posts.first().url.toHttpUrl())
                name = chapter.name
                date_upload = chapter.posts.first().date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        if (chapters.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    url = storedUrl(comicHome.resolve("comic/")!!)
                    name = "Chapter 1"
                },
            )
        }
        // The archive lists chapters in the site's term order, which some comics never sorted; same-day ones keep it
        return chapters.sortedBy { it.date_upload }.reversed()
    }

    /**
     * Pages are the comic's posts in date order, as its Prev and Next links go. Many comics tag only the
     * first post of a chapter, so a post without a chapter belongs to the chapter of the tagged post
     * before it, and posts before the first tagged one belong to the first chapter. A comic with no
     * chapters is one chapter of every post.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = comicUrl(chapter.url)
        val start = chapterUrl.toString()
        val comicHome = chapterUrl.resolve("/")!!
        val archiveChapters = client.get(archiveUrl(comicHome), Fetch.DOCUMENT).asJsoup().archiveChapters()
        val owner = archiveChapters.flatMap { archived -> archived.posts.map { it.url to archived.posts.first().url } }.toMap()
        val own = archiveChapters.firstOrNull { it.posts.first().url == start }
        val chapterless = start == comicHome.resolve("comic/").toString()
        check(if (chapterless) archiveChapters.isEmpty() else own != null) { "Chapter not found, refresh the chapter list" }
        val ownMonths = own?.posts.orEmpty().map { YearMonth.from(it.date) }
        val ownPosts = ownMonths.size

        // The first chapter reads from the comic's first post, so it also takes the untagged posts before it
        val isFirst = chapterless || archiveChapters.minBy { it.posts.first().date } === own
        var period = if (isFirst) null else Period(ownMonths.first())
        var url: HttpUrl? = listingUrl(comicHome, period)
        var current = if (isFirst) start else null
        var seen = 0
        var lastPost: String? = null
        val pages = mutableListOf<String>()
        while (url != null) {
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            val articles = document.select("article.type-comic")
            for (article in articles) {
                val post = article.selectFirst(".entry-title a")!!.absUrl("href")
                lastPost = post
                owner[post]?.let { current = it }
                if (current == start) {
                    pages += article.selectFirst("img.wp-post-image")!!.absUrl("src")
                    if (owner[post] == start) seen++
                } else if (seen == ownPosts) {
                    return pages.toPages()
                }
            }
            val next = document.selectFirst("a.next.page-numbers")?.absUrl("href")?.toHttpUrl()
            val nextOwnMonth = ownMonths.getOrNull(seen)?.takeIf { seen > 0 }
            val listed = period
            url = when {
                listed == null -> next
                // Posts of this chapter tagged further on, past other chapters' posts
                current != start && nextOwnMonth != null && nextOwnMonth > listed.last -> {
                    period = Period(nextOwnMonth)
                    listingUrl(comicHome, period)
                }
                next != null -> next
                current != start -> null
                else -> {
                    // A period without posts is a break: go on from the month of the post after the last one read
                    period = (if (articles.isEmpty()) nextPostMonth(lastPost!!)?.let(::Period) else listed.next())
                        ?.takeIf { it.first > listed.last && it.first <= YearMonth.now() }
                    period?.let { listingUrl(comicHome, it) }
                }
            }
        }
        return pages.toPages()
    }

    private suspend fun nextPostMonth(post: String): YearMonth? {
        val next = client.get(post, Fetch.DOCUMENT).asJsoup()
            .selectFirst("a.comic-nav-next")?.absUrl("href")
            ?: return null
        val date = client.get(next, Fetch.DOCUMENT).asJsoup().selectFirst("span.date")!!.text()
        return postDateFormat.parse(date, YearMonth::from)
    }

    private fun List<String>.toPages() = mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }

    private class ArchiveChapter(val name: String, val posts: List<ArchivePost>)

    private class ArchivePost(val url: String, val date: LocalDate)

    private fun Document.archiveChapters(): List<ArchiveChapter> = select(".comic-archive-chapter-wrap").map { wrap ->
        ArchiveChapter(
            name = wrap.selectFirst(".comic-archive-chapter")!!.text(),
            posts = wrap.select(".comic-list").map { post ->
                ArchivePost(
                    url = post.selectFirst(".comic-archive-title a")!!.absUrl("href"),
                    date = dateFormat.parse(post.selectFirst(".comic-archive-date")!!.text(), LocalDate::from),
                )
            },
        )
    }

    // A date archive: one month, or a whole year when month is null
    private class Period(val year: Int, val month: Int?) {
        constructor(yearMonth: YearMonth) : this(yearMonth.year, yearMonth.monthValue)

        val first: YearMonth get() = YearMonth.of(year, month ?: 1)
        val last: YearMonth get() = YearMonth.of(year, month ?: 12)

        // The rest of the year a chapter starts in goes by month, later years whole
        fun next() = if (month != null && month < 12) Period(year, month + 1) else Period(year + 1, null)
    }

    private fun listingUrl(comicHome: HttpUrl, period: Period?) = comicHome.newBuilder()
        .apply {
            if (period != null) {
                addPathSegment(period.year.toString())
                period.month?.let { addPathSegment(it.toString().padStart(2, '0')) }
                addPathSegment("")
            }
        }
        .addQueryParameter("post_type", "comic")
        .addQueryParameter("orderby", "date")
        .addQueryParameter("order", "asc")
        .build()

    private fun archiveUrl(comicHome: HttpUrl) = comicHome.resolve("archive/")!!

    // Stored urls are "/<subdomain>/<path>" for "https://<subdomain>.kemono.cafe/<path>"
    private fun storedUrl(url: HttpUrl): String = "/${url.host.removeSuffix(".$siteHost")}${url.encodedPath}"

    private fun comicUrl(storedUrl: String): HttpUrl {
        val url = (baseUrl + storedUrl).toHttpUrl()
        return url.newBuilder()
            .host("${url.pathSegments.first()}.$siteHost")
            .removePathSegment(0)
            .build()
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
private val postDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
