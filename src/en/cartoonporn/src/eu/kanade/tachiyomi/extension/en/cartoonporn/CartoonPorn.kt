package eu.kanade.tachiyomi.extension.en.cartoonporn

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class CartoonPorn : Madara() {

    override val mangaSubString = "porncomic"

    override val supportsPostId = false

    override val supportsLatest = false

    override fun archiveSelector() = "div.item_content, div.item-thumb"

    override suspend fun getPopularManga(page: Int): MangasPage = htmlList(page, "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = htmlList(page, "recent")

    private suspend fun htmlList(page: Int, orderBy: String): MangasPage {
        val path = if (page == 1) "/porncomic/" else "/porncomic/page/$page/"
        val url = "$baseUrl$path".toHttpUrl().newBuilder()
            .addQueryParameter("m_orderby", orderBy)
            .apply { if (orderBy == "views") addQueryParameter("m_order", "desc") }
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select(archiveSelector()).mapNotNull { archiveManga(it, "") }
        val hasNext = document.selectFirst("a[href*='/porncomic/page/${page + 1}/']") != null
        return MangasPage(mangas, hasNext)
    }

    override fun archiveManga(element: Element, id: String): SManga? {
        val link = element.selectFirst("a[href*='/porncomic/']") ?: return null
        val href = link.attr("abs:href").takeIf(String::isNotBlank) ?: return null
        val mangaPath = href.toHttpUrl().encodedPath
        return SManga.create().apply {
            url = mangaPath
            title = link.attr("title").ifBlank { link.text() }.trim()
            thumbnail_url = element.selectFirst("img")?.let { imageFromElement(it) }
        }
    }

    override val mangaDetailsSelectorTitle = "h1.comic-hero__title"

    override val mangaDetailsSelectorThumbnail = ".comic-hero__image-wrap img"

    override val mangaDetailsSelectorArtist = ".meta-tag--artist"

    override val mangaDetailsSelectorGenre = ".meta-tag--genre"

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val comicSlug = mangaPath.trim('/').substringAfterLast('/')
        return document.select("a[href*=\"/porncomic/\"]")
            .map { it.attr("abs:href") }
            .filter { href ->
                val segments = try {
                    href.toHttpUrl().encodedPath.trim('/').split('/')
                } catch (_: Exception) {
                    return@filter false
                }
                segments.size == 3 && segments[0] == mangaSubString && segments[1] == comicSlug
            }
            .distinct()
            .mapNotNull { chapterFromElement(it, mangaPath) }
    }

    private fun chapterFromElement(href: String, mangaPath: String): SChapter? {
        val slug = try {
            href.toHttpUrl().encodedPath.trimEnd('/').substringAfterLast('/')
        } catch (_: Exception) {
            return null
        }
        return SChapter.create().apply {
            url = slug
            name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
            memo = buildJsonObject { put("mangaPath", mangaPath) }
        }
    }

    override fun parsePages(document: Document): List<Page> = document.select("img.manga-img")
        .mapNotNull { imageFromElement(it) }
        .filter { it.contains("/WP-manga/data/") }
        .distinct()
        .mapIndexed { index, url -> Page(index, document.location(), url) }
}
