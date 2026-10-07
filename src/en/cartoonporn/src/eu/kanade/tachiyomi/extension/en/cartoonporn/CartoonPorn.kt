package eu.kanade.tachiyomi.extension.en.cartoonpornto

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class CartoonPorn : KeiSource() {

    override val supportsLatest = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = buildListUrl(page, "views")
        val document = client.get(url).asJsoup()
        val mangas = document.select("a[href*=\"/porncomic/\"]")
            .filter { it.attr("abs:href").isComicUrl() }
            .distinctBy { it.attr("abs:href") }
            .map(::listingParse)
        return MangasPage(mangas, document.selectFirst("a[href*=\"/porncomic/page/${page + 1}/\"]") != null)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = buildListUrl(page, "recent")
        val document = client.get(url).asJsoup()
        val mangas = document.select("a[href*=\"/porncomic/\"]")
            .filter { it.attr("abs:href").isComicUrl() }
            .distinctBy { it.attr("abs:href") }
            .map(::listingParse)
        return MangasPage(mangas, document.selectFirst("a[href*=\"/porncomic/page/${page + 1}/\"]") != null)
    }

    private fun buildListUrl(page: Int, orderBy: String): String {
        val base = if (page == 1) "$baseUrl/porncomic/" else "$baseUrl/porncomic/page/$page/"
        return base.toHttpUrl().newBuilder()
            .addQueryParameter("m_orderby", orderBy)
            .apply { if (orderBy == "views") addQueryParameter("m_order", "desc") }
            .build().toString()
    }

    private fun String.isComicUrl(): Boolean {
        val path = try {
            toHttpUrl().encodedPath
        } catch (_: Exception) {
            return false
        }
        val segments = path.trim('/').split('/')
        return segments.size == 2 && segments[0] == "porncomic"
    }

    private fun listingParse(element: Element): SManga = SManga.create().apply {
        url = element.attr("abs:href").toHttpUrl().encodedPath.removePrefix("/")
        val rawTitle = element.attr("title").ifBlank {
            element.selectFirst("img")?.attr("alt").orEmpty()
        }.trim()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst("img")?.attr("src")?.ifEmpty { null }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)
        val body = FormBody.Builder()
            .add("action", "wp-manga-search-manga")
            .add("title", query)
            .build()
        val response = client.post("$baseUrl/wp-admin/admin-ajax.php", body).parseAs<SearchResponse>()
        val mangas = response.data.map {
            SManga.create().apply {
                url = it.url.toHttpUrl().encodedPath.removePrefix("/")
                title = it.title
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/${manga.url}").asJsoup()

        val updatedManga = manga.apply {
            val rawTitle = document.selectFirst("h1")?.text()?.trim().orEmpty()
            check(rawTitle.isNotBlank()) { "Empty title for ${manga.url}" }
            title = rawTitle
            thumbnail_url = document.selectFirst(".comic-hero__image-wrap img")
                ?.attr("src")?.ifEmpty { null }
        }

        val updatedChapters = document.select("a[href*=\"/porncomic/\"]")
            .map { it.attr("abs:href") }
            .filter { href ->
                val path = try {
                    href.toHttpUrl().encodedPath
                } catch (_: Exception) {
                    return@filter false
                }
                val segments = path.trim('/').split('/')
                segments.size == 3 && segments[0] == "porncomic" && segments[1] == manga.url.trim('/').split('/').last()
            }
            .distinct()
            .map { href ->
                SChapter.create().apply {
                    url = href.toHttpUrl().encodedPath.removePrefix("/")
                    name = href.trim('/').split('/').last()
                        .replace('-', ' ')
                        .replaceFirstChar { it.uppercase() }
                }
            }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/${chapter.url}").asJsoup()
        .select("img.manga-img")
        .map { it.attr("src").ifEmpty { it.attr("data-src") } }
        .filter { it.isNotBlank() }
        .distinct()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}
