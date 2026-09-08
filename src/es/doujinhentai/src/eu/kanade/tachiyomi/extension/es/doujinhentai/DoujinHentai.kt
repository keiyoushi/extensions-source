package eu.kanade.tachiyomi.extension.es.doujinhentai

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DoujinHentai : KeiSource() {

    private val chapterDateFormat = DateTimeFormatter.ofPattern("d MMM. yyyy", Locale.ENGLISH)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mangaFromElement(element: Element): SManga? {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.selectFirst("h3.font-bold")?.text() ?: return null
        manga.thumbnail_url = element.selectFirst("img")?.let { img ->
            img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
        }
        return manga
    }

    private fun mangasPageFromDocument(document: Document): MangasPage {
        val mangas = document
            .select("div.group.bg-white.rounded-2xl a.block")
            .mapNotNull { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ── Popular ───────────────────────────────────────────────────────────────

    override suspend fun getPopularManga(page: Int): MangasPage = mangasPageFromDocument(client.get("$baseUrl/lista-manga-hentai?orderby=views&page=$page").asJsoup())

    // ── Latest ────────────────────────────────────────────────────────────────

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangasPageFromDocument(client.get("$baseUrl/lista-manga-hentai?orderby=last&page=$page").asJsoup())

    // ── Search ────────────────────────────────────────────────────────────────
    // El endpoint /search?query= devuelve JSON (live-search), no HTML.
    // La búsqueda real por texto usa /lista-manga-hentai?search=<query>
    //
    // Cuando hay query de texto se ignoran los filtros (limitación del sitio).
    // Los filtros de ruta son mutuamente excluyentes; se aplica el primero
    // con valor en este orden: género > artista > autor > scanlator > letra > tipo.
    // El filtro de ordenación solo aplica cuando no hay otro filtro de ruta.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addPathSegment("lista-manga-hentai")
            url.addQueryParameter("search", query)
            url.addQueryParameter("page", page.toString())
            return mangasPageFromDocument(client.get(url.build()).asJsoup())
        }

        var genre = ""
        var artist = ""
        var author = ""
        var scanlator = ""
        var letter = ""
        var type = ""
        var orderBy = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                is ArtistFilter -> artist = filter.state.trim()
                is AuthorFilter -> author = filter.state.trim()
                is ScanlatorFilter -> scanlator = filter.state.trim()
                is LetterFilter -> letter = filter.toUriPart()
                is TypeFilter -> type = filter.toUriPart()
                is SortFilter -> orderBy = filter.toUriPart()
                else -> {}
            }
        }

        when {
            genre.isNotEmpty() -> url.addPathSegments("lista-manga-hentai/category/$genre")
            artist.isNotEmpty() -> url.addPathSegments("lista-manga-hentai/artist/${artist.replace(" ", "%20")}")
            author.isNotEmpty() -> url.addPathSegments("lista-manga-hentai/author/${author.replace(" ", "%20")}")
            scanlator.isNotEmpty() -> url.addPathSegments("user/${scanlator.replace(" ", "%20")}")
            letter.isNotEmpty() -> url.addPathSegments("lista-manga-hentai/letra/$letter")
            type.isNotEmpty() -> url.addPathSegment("lista-de-$type")
            else -> {
                url.addPathSegment("lista-manga-hentai")
                if (orderBy.isNotEmpty()) url.addQueryParameter("orderby", orderBy)
            }
        }

        url.addQueryParameter("page", page.toString())
        return mangasPageFromDocument(client.get(url.build()).asJsoup())
    }

    // ── Manga details ──────────────────────────────────────────────────────────
    // "Autor(es)"  → <a rel="author" href=".../author/...">
    // "Artista(s)" → <a href=".../artist/...">  (sin rel="author")
    // Algunos títulos solo tienen artista, otros solo autor, otros ambos.
    private fun mangaDetailsParse(document: Document): SManga {
        val main: Element = document.selectFirst("main#main-content") ?: document.body()
        val manga = SManga.create()

        manga.title = main.selectFirst("h1")!!.text()

        val authors = main.select("a[rel=author]").texts()
        val artists = main.select("a[href*='/artist/']").texts()

        manga.author = authors.ifEmpty { artists }.joinToString(", ").ifEmpty { null }
        manga.artist = artists.ifEmpty { authors }.joinToString(", ").ifEmpty { null }

        manga.description = main.selectFirst("div.prose")?.text()

        val categories = main.select("a[rel=tag][href*='/category/']").texts()
        val tags = main.select("a[rel=tag][href*='/tag/']").mapNotNull {
            it.text().trimStart('#').takeIf { t -> t.isNotBlank() }
        }
        manga.genre = (categories + tags).distinct().joinToString(", ").ifEmpty { null }

        val statusText = main.selectFirst("span[aria-label^=Estado]")?.text()
            ?: main.selectFirst("div.absolute span")?.text() ?: ""
        manga.status = when {
            statusText.contains("Ongoing", ignoreCase = true) ||
                statusText.contains("En curso", ignoreCase = true) -> SManga.ONGOING
            statusText.contains("Complet", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        manga.thumbnail_url = main.selectFirst("figure img")?.attr("abs:src")
        manga.initialized = true
        return manga
    }

    // ── Chapter list ───────────────────────────────────────────────────────────
    // El slug del capítulo NO siempre contiene "chapter" (ej: /roman, /bokura)
    private fun chapterListParse(document: Document): List<SChapter> = document
        .select("div.flex.items-center.gap-4.p-3.mb-2.border.rounded-lg")
        .mapNotNull { chapterFromElement(it) }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "manga-hentai") {
            return null
        }

        val mangaSlug = url.encodedPathSegments.getOrNull(1) ?: return null
        val mangaUrl = "$baseUrl/manga-hentai/$mangaSlug".toHttpUrl()

        return mangaDetailsParse(client.get(mangaUrl).asJsoup()).apply {
            setUrlWithoutDomain(mangaUrl.toString())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        val chapterLink = element.selectFirst("div.flex-1 > a.font-bold")
            ?: element.selectFirst("div.flex-1 a")
        chapterLink?.let { chapter.setUrlWithoutDomain(it.attr("href")) }

        val baseName = chapterLink?.text()?.removePrefix("Leer ") ?: ""
        val subTitle = element.selectFirst("div.flex-1 div.text-sm.font-medium")?.text() ?: ""
        chapter.name = if (subTitle.isNotEmpty() && subTitle != baseName) "$baseName: $subTitle" else baseName

        chapter.scanlator = element.select("div.text-sm.text-right a[href*='/user/']")
            .firstOrNull()?.text()

        val dateText = element.select("div.text-sm.text-right span.font-medium")
            .lastOrNull()?.text()
        if (!dateText.isNullOrEmpty()) {
            chapter.date_upload = chapterDateFormat.tryParseDate(dateText)
        }

        return chapter
    }

    // ── Pages ──────────────────────────────────────────────────────────────────
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        // Estrategia 1: JSON embebido → const pageUrls = {"1":"url",...};
        document.select("script").asSequence()
            .map { it.data() }
            .firstOrNull { it.contains("pageUrls") }
            ?.let { script ->
                val json = PAGE_URLS_JSON_REGEX.find(script)?.groupValues?.get(1)
                if (json != null) {
                    val pages = PAGE_ENTRIES_REGEX
                        .findAll(json)
                        .map { Page(it.groupValues[1].toInt() - 1, imageUrl = it.groupValues[2].replace("\\/", "/")) }
                        .sortedBy { it.index }
                        .toList()
                    if (pages.isNotEmpty()) return pages
                }
            }

        // Estrategia 2: imágenes pre-renderizadas en el HTML
        document.select("div#vertical-pages-container div[data-page] img")
            .takeIf { it.isNotEmpty() }
            ?.let { imgs ->
                return imgs.mapIndexed { idx, img ->
                    Page(idx, imageUrl = img.attr("abs:src").ifEmpty { img.attr("src") })
                }
            }

        // Estrategia 3: single page mode
        return document.select("div.single-page-mode img.manga-image")
            .mapIndexed { idx, img ->
                Page(idx, imageUrl = img.attr("abs:src").ifEmpty { img.attr("src") })
            }
    }

    // ── Filters Layout────────────────────────────────────────────────────────────────
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("La búsqueda por texto ignora los filtros"),
        Filter.Header("Los filtros de ruta son mutuamente excluyentes"),
        Filter.Separator(),
        GenreFilter(),
        Filter.Separator(),
        TypeFilter(),
        Filter.Separator(),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Buscar por artista o autor exacto (ej: saigado, milftoon)"),
        ArtistFilter(),
        AuthorFilter(),
        Filter.Separator(),
        Filter.Header("Buscar por scanlator/usuario exacto (ej: NekoCreme, Fritz Translations)"),
        ScanlatorFilter(),
        Filter.Separator(),
        Filter.Header("Filtrar por primera letra del título"),
        LetterFilter(),
    )

    companion object {
        private val PAGE_URLS_JSON_REGEX = Regex("""const pageUrls\s*=\s*(\{[^;]+\})""")
        private val PAGE_ENTRIES_REGEX = Regex(""""(\d+)"\s*:\s*"([^"]+)"""")
    }
}

private fun Elements.texts(): List<String> = this.mapNotNull { it.text().takeIf { t -> t.isNotBlank() } }
