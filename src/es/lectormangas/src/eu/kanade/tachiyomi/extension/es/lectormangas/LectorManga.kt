package eu.kanade.tachiyomi.extension.es.lectormangas

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.net.URLEncoder

@Source
abstract class LectorManga : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/comics" else "$baseUrl/comics?page=$page"
        return parseListing(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl?page=$page"
        return parseListing(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$baseUrl/comics?search=$encoded&page=$page"
        return parseListing(client.get(url).asJsoup())
    }

    private fun parseListing(document: org.jsoup.nodes.Document): MangasPage {
        val mangas = document.select("a[href^=/comics/]")
            .mapNotNull { element ->
                val href = element.attr("abs:href")
                val path = href.toHttpUrl().encodedPath
                val segments = path.trim('/').split('/')
                // Series URLs have /comics/<slug>; chapter URLs have one more segment.
                if (segments.size != 2) return@mapNotNull null

                val title = element.selectFirst("h2,h3,h4,.title,[class*=title]")?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: element.text().trim().takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                SManga.create().apply {
                    this.title = title
                    setUrlWithoutDomain(href)
                    thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                        ?: element.selectFirst("img")?.attr("abs:data-src")
                }
            }
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst(
            "a[rel=next], .pagination a:contains(Siguiente), .pagination a:contains(›)"
        ) != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (!url.encodedPath.startsWith("/comics/")) return null

        val manga = SManga.create().apply {
            setUrlWithoutDomain(url.toString())
        }
        return fetchMangaUpdate(
            manga,
            emptyList(),
            fetchDetails = true,
            fetchChapters = true,
        ).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        manga.apply {
            title = document.selectFirst("h1")?.text()?.trim()
                ?: title
            thumbnail_url = document.selectFirst(
                "meta[property=og:image]"
            )?.attr("content")
                ?: document.selectFirst("img")?.attr("abs:src")
                ?: thumbnail_url

            description = document.selectFirst(
                "meta[property=og:description]"
            )?.attr("content")
                ?: document.selectFirst(
                    ".description,.sinopsis,#sinopsis,[class*=description]"
                )?.text()?.trim()

            genre = document.select(
                "a[href*=genero],a[href*=genre],.genre a,.genres a"
            ).eachText().distinct().joinToString(", ")

            status = when {
                document.text().contains("En emisión", true) ->
                    SManga.ONGOING
                document.text().contains("Finalizado", true) ||
                    document.text().contains("Completado", true) ->
                    SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = parseChapters(document)
        return SMangaUpdate(manga, chapterList)
    }

    private fun parseChapters(document: org.jsoup.nodes.Document): List<SChapter> {
        return document.select(
            "a[href*='/capitulo-'], a[href*='/capitulo/'], " +
                "a[href*='/chapter-'], a[href*='/chapter/']"
        )
            .mapNotNull { element ->
                val href = element.attr("abs:href")
                if (href.isBlank()) return@mapNotNull null

                val name = element.text().trim().ifBlank {
                    href.toHttpUrl().pathSegments.lastOrNull() ?: "Capítulo"
                }

                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    this.name = name
                    scanlator = element.closest("div,li,article")?.selectFirst(
                        ".scanlator,.group,.team,[class*=group]"
                    )?.text()?.trim()
                }
            }
            .distinctBy { it.url }
            .reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        val imageElements = document.select(
            "img[src*='media.ikigaicomics.lat'], " +
                "img[data-src*='media.ikigaicomics.lat'], " +
                "img[data-lazy-src*='media.ikigaicomics.lat']"
        )

        val pages = imageElements.mapNotNull { element ->
            val image = element.attr("abs:src")
                .ifBlank { element.attr("abs:data-src") }
                .ifBlank { element.attr("abs:data-lazy-src") }
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            Page(pagesIndex(imageElements, element), imageUrl = image)
        }

        if (pages.isNotEmpty()) return pages

        // Fallback for a future CDN/domain change.
        return document.select("img").mapIndexedNotNull { index, element ->
            val image = element.attr("abs:src")
                .ifBlank { element.attr("abs:data-src") }
                .takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: return@mapIndexedNotNull null
            Page(index, imageUrl = image)
        }
    }

    private fun pagesIndex(elements: List<Element>, element: Element): Int =
        elements.indexOf(element).coerceAtLeast(0)
}
