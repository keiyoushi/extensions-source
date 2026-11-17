package eu.kanade.tachiyomi.extension.fr.mangaplexe

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class Mangaplexe : KeiSource() {

    // The site stopped publishing in July 2022
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getEntries(themes = false), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()
        return MangasPage(getEntries(category?.themes == true, category?.series, query.trim()), false)
    }

    // The site's lists, a series' heroines, and an entry from its category (any page of it) or one of its images
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter(String::isNotEmpty)
            .let { if (it.firstOrNull() == "category") it.drop(1) else it }
            .let { if (it.size > 2 && it[it.lastIndex - 1] == "page") it.dropLast(2) else it }
        val mangas = when {
            segments == listOf(HEROINES) || segments == listOf(SERIES) -> getEntries(themes = false)
            segments == listOf(THEMES) -> getEntries(themes = true)
            segments.size == 2 && segments[0] == SERIES -> getEntries(themes = false, series = segments[1])
            segments.size >= 3 && segments[0] == SERIES -> getEntry(themes = false, segments.take(3))
            segments.size >= 2 && segments[0] == THEMES -> getEntry(themes = true, segments.take(2))
            else -> emptyList()
        }
        return MangasPage(mangas, false)
    }

    private suspend fun getEntry(themes: Boolean, segments: List<String>): List<SManga> {
        val url = "/category/${segments.joinToString("/")}/"
        return getEntries(themes).filter { it.url == url }
    }

    // The site publishes single images, filed by heroine (under her series) or by theme: each of these is an entry
    private suspend fun getEntries(themes: Boolean, series: String? = null, query: String = ""): List<SManga> {
        val document = client.get("$baseUrl/${if (themes) THEMES else HEROINES}/", Fetch.DOCUMENT).asJsoup()
        // The themes page also links a lone image outside of any category
        return document.select("figure.wp-caption:has(a[href*=/category/])")
            .filter { series == null || it.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments[2] == series }
            .filter { it.selectFirst("figcaption")!!.text().contains(query, ignoreCase = true) }
            .map { it.toSManga() }
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(selectFirst("a")!!.absUrl("href"))
        val caption = selectFirst("figcaption")!!
        // A heroine's caption is her name in bold, then her series
        title = caption.selectFirst("b")?.text() ?: caption.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")?.replace(SIZE, "")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trimEnd('/').substringAfterLast('/')
        val category = client.get(api("categories", "slug" to slug, "_embed" to "up"), Fetch.XHR).parseAs<List<CategoryDto>>().first()
        val details = category.toSManga(manga)
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        // Oldest first; the largest category has 51 images and the archive no longer changes
        val posts = client.get(
            api("posts", "categories" to category.id.toString(), "order" to "asc", "per_page" to "100", "_fields" to "date_gmt,featured_media"),
            Fetch.XHR,
        ).parseAs<List<PostDto>>()
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Images"
            date_upload = DATE_FORMAT.tryParseDateTime(posts.last().dateGmt, ZoneOffset.UTC)
            // Each post's image is its featured image
            memo = buildJsonObject { put("media", posts.joinToString(",") { it.featuredMedia.toString() }) }
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(
        api("media", "include" to chapter.memo.getString("media"), "orderby" to "include", "per_page" to "100", "_fields" to "source_url"),
        Fetch.XHR,
    ).parseAs<List<MediaDto>>().mapIndexed { index, media -> Page(index, imageUrl = media.sourceUrl) }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private fun api(path: String, vararg parameters: Pair<String, String>) = "$baseUrl/wp-json/wp/v2".toHttpUrl().newBuilder()
        .addPathSegment(path)
        .apply { parameters.forEach { (name, value) -> addQueryParameter(name, value) } }
        .build()

    companion object {
        private const val HEROINES = "heroines-hentai"
        private const val SERIES = "parodies-hentai"
        private const val THEMES = "porno-hentai"
        private val SIZE = Regex("""-\d+x\d+(?=\.\w+$)""")
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
