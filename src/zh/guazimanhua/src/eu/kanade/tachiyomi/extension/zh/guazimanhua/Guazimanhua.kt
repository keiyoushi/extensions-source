package eu.kanade.tachiyomi.extension.zh.guazimanhua

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
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Guazimanhua : KeiSource() {

    // The site serves a "download our app" page (no images) for the latest chapter when
    // it detects an Android mobile browser UA; a desktop UA returns the actual pages.
    override fun Headers.Builder.configureHeaders(): Headers.Builder = set(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category.php?sort=hits&page=$page").asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/category.php?sort=update&page=$page").asJsoup()
        return parseMangaList(document)
    }

    override fun getFilterList(data: JsonElement?): FilterList = buildFilterList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = "$baseUrl/category.php".toHttpUrl().newBuilder()
        if (query.isNotEmpty()) {
            builder.addQueryParameter("keyword", query)
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
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.card").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3 a")!!.text()
                setUrlWithoutDomain(element.selectFirst("a.cover-wrap")!!.attr("href"))
                thumbnail_url = element.selectFirst("img.cover")?.attr("src")
                author = element.selectFirst("div.meta")?.ownText()
                    ?.substringBefore(" · ")
                    ?.takeIf { it.isNotEmpty() }
            }
        }
        val hasNextPage = document.select("nav.pager a").any { it.text() == ">" }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (!url.encodedPath.startsWith("/comic.php")) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        manga.apply {
            document.selectFirst("div.mobile-comic-title")?.text()?.let { title = it }
            document.selectFirst("img.mobile-comic-cover")?.absUrl("src")?.let { thumbnail_url = it }
            document.selectFirst("p.mobile-comic-desc")?.text()?.let { description = it }
            document.selectFirst("p.mobile-comic-tags")?.text()?.let { genre = it }
            document.select("div.cinema-strip > div")
                .firstOrNull { it.selectFirst("span")?.text() == "作者" }
                ?.selectFirst("b")
                ?.text()
                ?.takeIf { it.isNotEmpty() }
                ?.let { author = it }
            val meta = document.selectFirst("p.mobile-comic-meta")?.text().orEmpty()
            status = when {
                meta.contains("完结") -> SManga.COMPLETED
                meta.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
        val chapterList = document.select("section.mobile-comic-all-chapters div.mobile-chapter-grid a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
            }
        }
        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        return document.select("section.reader-images img").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("abs:src"))
        }
    }
}
