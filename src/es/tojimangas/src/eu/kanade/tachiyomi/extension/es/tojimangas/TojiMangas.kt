package eu.kanade.tachiyomi.extension.es.tojimangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class TojiMangas : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "es-ES,es;q=0.9")

    // ──── Popular (Más vistas) ────

    override fun popularMangaRequest(page: Int): Request = GET(
        "$baseUrl/biblioteca".toHttpUrl().newBuilder()
            .addQueryParameter("orderby", "views")
            .addQueryParameter("page", page.toString())
            .build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage = parseBiblioteca(response)

    // ──── Latest (Actualización reciente) ────

    override fun latestUpdatesRequest(page: Int): Request = GET(
        "$baseUrl/biblioteca".toHttpUrl().newBuilder()
            .addQueryParameter("orderby", "recent")
            .addQueryParameter("page", page.toString())
            .build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseBiblioteca(response)

    // ──── Search ────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/buscar".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseBiblioteca(response)

    // ──── Biblioteca parser (shared) ────

    private fun parseBiblioteca(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.serie-card[href]").mapNotNull { card ->
            val href = card.attr("abs:href")
            val slug = href.substringAfter("/manga/").trimEnd('/').takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            val title = card.selectFirst("h3.title")?.text()?.trim()
                ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                this.url = "/manga/$slug"
                thumbnail_url = card.selectFirst("figure.cover img[src]")?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst("nav[aria-label*=Pagination] a[rel=next], a[rel=next]") != null ||
            document.selectFirst("nav.pagination a:containsOwn(Siguiente), nav[aria-label*=Pagination] a:containsOwn(Next)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ──── Manga Details ────

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.text-2xl")?.text()?.trim()
                ?: document.title().substringBefore("—").trim()

            thumbnail_url = document.selectFirst("section img[alt][src]")?.attr("abs:src")
                ?: document.selectFirst("figure img[src]")?.attr("abs:src")

            description = document.selectFirst("section p.leading-relaxed")?.text()?.trim()

            genre = document.select("a.card-genre")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .ifBlank { null }

            status = parseStatus(document.selectFirst(".badge-ghost")?.text())
        }
    }

    private fun parseStatus(text: String?): Int = when {
        text.isNullOrBlank() -> SManga.UNKNOWN
        text.contains("En curso", true) || text.contains("en emisión", true) -> SManga.ONGOING
        text.contains("Completado", true) || text.contains("Finalizado", true) -> SManga.COMPLETED
        text.contains("Pausa", true) || text.contains("Hiatus", true) -> SManga.ON_HIATUS
        text.contains("Abandonado", true) || text.contains("Cancel", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ──── Chapter List ────

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaPath = response.request.url.encodedPath.trimEnd('/')

        // The full chapter list is rendered server-side into #chapter-grid
        return document.select("#chapter-grid a[href]").mapNotNull { link ->
            val href = link.attr("href")
            val chapterNumber = href.trimEnd('/').substringAfterLast('/')
                .toFloatOrNull() ?: return@mapNotNull null

            SChapter.create().apply {
                name = link.selectFirst("div.truncate")?.text()?.trim()
                    ?.ifEmpty { null }
                    ?: "Capítulo $chapterNumber"
                url = href.substringAfter(baseUrl)
                chapter_number = chapterNumber
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ──── Page List ────

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headers.newBuilder()
            .removeAll("Referer")
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build(),
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val pages = document.select("main img[src]")
            .mapNotNull { it.attr("abs:src").takeIf(String::isNotBlank) }
            .filter { it.contains("cdn.lectortmoo.com") || it.contains("WP-manga/data") || it.contains("leercapitulo") }
            .distinct()

        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()
}
