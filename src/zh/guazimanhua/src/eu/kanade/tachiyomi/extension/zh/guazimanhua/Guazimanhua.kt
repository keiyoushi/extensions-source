package eu.kanade.tachiyomi.extension.zh.guazimanhua

import android.content.SharedPreferences
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
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/**
 * 瓜子漫画 (www.guazimanhua.com) — custom mobile template
 *
 * - Category / popular / latest / search share /category.php (sort=hits|update|daily|score)
 * - Details /comic.php?id={id} carries two identical chapter lists (desktop all-chapter-grid and
 *   mobile mobile-chapter-grid), newest first; the mobile one is parsed
 * - Pages are img[src] inside section.reader-images of /chapter.php?id={id}, already absolute URLs
 * - The newest chapter is sometimes served without any reader-images block: the app gets it, the
 *   web does not, so it is fetched from the app API instead, see [GuaziAppApi]
 * - The "download the app" prompt shown after a few chapters is rendered client-side from
 *   localStorage and the server does not gate image requests, so it needs no handling here
 * - User-Agent is irrelevant: desktop, mobile and Dalvik UAs return byte-identical responses,
 *   so no custom User-Agent is set
 *
 * name / lang / id / baseUrl are injected from the keiyoushi block in build.gradle.kts.
 */
@Source
abstract class Guazimanhua : KeiSource() {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val appApi by lazy { GuaziAppApi(client, headers, preferences) }

    // ---- Popular and latest ----

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category.php?sort=hits&page=$page").asJsoup()
        return MangasPage(parseMangaList(document), document.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/category.php?sort=update&page=$page").asJsoup()
        return MangasPage(parseMangaList(document), document.hasNextPage())
    }

    // ---- Search and category filters ----

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val keyword = query.trim()
        val builder = "$baseUrl/category.php".toHttpUrl().newBuilder()
        if (keyword.isNotEmpty()) {
            builder.addQueryParameter("keyword", keyword)
        }
        var sort = "hits"
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> if (filter.state != 0) builder.addQueryParameter("cid", filter.toString())
                is RegionFilter -> if (filter.state != 0) builder.addQueryParameter("city", filter.toString())
                is AudienceFilter -> if (filter.state != 0) builder.addQueryParameter("audience", filter.toString())
                is StatusFilter -> if (filter.state != 0) builder.addQueryParameter("is_end", filter.toString())
                is SortFilter -> sort = filter.toString()
                else -> {}
            }
        }
        builder.addQueryParameter("sort", sort)
        builder.addQueryParameter("page", page.toString())
        val document = client.get(builder.build()).asJsoup()
        return MangasPage(parseMangaList(document), document.hasNextPage())
    }

    // ---- Details and chapters (one page, one request) ----

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseDetails(document, manga), parseChapterList(document))
    }

    // ---- URL search (pasting a site link into the search box) ----

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (!url.encodedPath.startsWith("/comic.php")) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ---- Pages ----

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val urls = document.select("section.reader-images img").map { it.absUrl("src") }
            .ifEmpty { appApi.pageUrls(chapterId(chapter)) }
        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    // ---- Filters ----

    override fun getFilterList(data: JsonElement?): FilterList = buildFilterList()

    // ---- Parsing ----

    /** List card: article.card > a.cover-wrap (link and cover), h3 a (title), div.meta (author first) */
    private fun parseMangaList(document: Document): List<SManga> = document.select("article.card")
        .mapNotNull { card ->
            val link = card.selectFirst("h3 a") ?: return@mapNotNull null
            SManga.create().apply {
                url = link.attr("href")
                title = link.text()
                thumbnail_url = card.selectFirst("img.cover")?.absUrl("src")
                author = card.selectFirst("div.meta")?.ownText()
                    ?.substringBefore(" · ")
                    ?.takeIf { it.isNotEmpty() }
            }
        }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        document.selectFirst("div.mobile-comic-title")?.text()?.let { title = it }
        document.selectFirst("img.mobile-comic-cover")?.absUrl("src")?.let { thumbnail_url = it }
        document.selectFirst("p.mobile-comic-desc")?.text()?.let { description = it }
        document.selectFirst("p.mobile-comic-tags")?.text()?.let { genre = it }
        author = document.select("div.cinema-strip > div")
            .firstOrNull { it.selectFirst("span")?.text() == "作者" }
            ?.selectFirst("b")
            ?.text()
            ?.takeIf { it.isNotEmpty() }
        // p.mobile-comic-meta looks like "连载·341话" / "完结·120话"
        val meta = document.selectFirst("p.mobile-comic-meta")?.text().orEmpty()
        status = when {
            meta.contains("完结") -> SManga.COMPLETED
            meta.contains("连载") -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    /**
     * The site lists chapters newest first; they are reversed to oldest first so that the position
     * based numbering below increases with the site's own chapter numbers.
     *
     * Titles carry the site numbering, but the list also holds unnumbered entries (公告 / 预告 /
     * 角色档案), so numbering by list position is the only way to keep it unique and monotonic.
     */
    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("section.mobile-comic-all-chapters div.mobile-chapter-grid a")
        .asReversed()
        .mapIndexed { index, element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.text()
                chapter_number = (index + 1).toFloat()
            }
        }

    /** The pager drops its ">" link on the last page (checked: hits page 635 has none, page 636 is empty) */
    private fun Document.hasNextPage(): Boolean = select("nav.pager a").any { it.text() == ">" }

    /** SChapter.url looks like /chapter.php?id=1633768; the app API takes that same chapter id */
    private fun chapterId(chapter: SChapter): String = chapter.url.substringAfter("id=", "").substringBefore('&')
}
