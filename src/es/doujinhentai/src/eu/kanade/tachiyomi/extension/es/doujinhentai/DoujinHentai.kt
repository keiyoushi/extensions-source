package eu.kanade.tachiyomi.extension.es.doujinhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinHentai :
    Madara(
        "DoujinHentai",
        "https://doujinhentai.net",
        "es",
        SimpleDateFormat("d MMM. yyyy", Locale.ENGLISH),
    ) {

    override val fetchGenres = false

    // SimpleDateFormat no es thread-safe; se declara con by lazy para
    // reutilizarlo sin reinstanciarlo en cada capítulo.
    private val chapterDateFormat by lazy { SimpleDateFormat("d MMM. yyyy", Locale.ENGLISH) }

    // ── Popular ──────────────────────────────────────────────────────────────
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=views&page=$page", headers)

    override fun popularMangaSelector() = "div.group.bg-white.rounded-2xl a.block"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.selectFirst("h3.font-bold")?.text()?.trim() ?: ""
        manga.thumbnail_url = element.selectFirst("img")?.let { img ->
            img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    // ── Latest ───────────────────────────────────────────────────────────────
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=last&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ── Search ───────────────────────────────────────────────────────────────
    // El endpoint /search?query= devuelve JSON (live-search), no HTML.
    // La búsqueda real por texto usa /lista-manga-hentai?search=<query>
    //
    // Cuando hay query de texto se ignoran los filtros (limitación del sitio).
    // Los filtros de ruta son mutuamente excluyentes; se aplica el primero
    // con valor en este orden: género > artista > autor > scanlator > letra > tipo.
    // El filtro de ordenación solo aplica cuando no hay otro filtro de ruta.
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addPathSegment("lista-manga-hentai")
            url.addQueryParameter("search", query)
            url.addQueryParameter("page", page.toString())
            return GET(url.build(), headers)
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
            // Scanlator → /user/{nombre}?page=N
            // La página de usuario usa exactamente el mismo HTML de tarjetas
            // que el listado general, por lo que los selectores existentes funcionan.
            scanlator.isNotEmpty() -> {
                url.addPathSegments("user/${scanlator.replace(" ", "%20")}")
            }
            letter.isNotEmpty() -> url.addPathSegments("lista-manga-hentai/letra/$letter")
            type.isNotEmpty() -> url.addPathSegment("lista-de-$type")
            else -> {
                url.addPathSegment("lista-manga-hentai")
                if (orderBy.isNotEmpty()) url.addQueryParameter("orderby", orderBy)
            }
        }

        url.addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ── Manga details ─────────────────────────────────────────────────────────
    // "Autor(es)"  → <a rel="author" href=".../author/...">
    // "Artista(s)" → <a href=".../artist/...">  (sin rel="author")
    // Algunos títulos solo tienen artista, otros solo autor, otros ambos.
    override fun mangaDetailsParse(document: Document): SManga {
        val main: Element = document.selectFirst("main#main-content") ?: document.body()
        val manga = SManga.create()

        manga.title = main.selectFirst("h1")?.text()?.trim() ?: ""

        val authors = main.select("a[rel=author]")
            .map { it.text().trim() }.filter { it.isNotEmpty() }
        val artists = main.select("a[href*='/artist/']")
            .map { it.text().trim() }.filter { it.isNotEmpty() }

        manga.author = authors.ifEmpty { artists }.joinToString(", ").ifEmpty { null }
        manga.artist = artists.ifEmpty { authors }.joinToString(", ").ifEmpty { null }

        manga.description = main.selectFirst("div.prose")?.text()?.trim()

        val categories = main.select("a[rel=tag][href*='/category/']")
            .map { it.text().trim() }.filter { it.isNotEmpty() }
        val tags = main.select("a[rel=tag][href*='/tag/']")
            .map { it.text().trimStart('#').trim() }.filter { it.isNotEmpty() }
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

    // ── Chapter list ─────────────────────────────────────────────────────────
    // El slug del capítulo NO siempre contiene "chapter" (ej: /roman, /bokura)
    override fun chapterListSelector() = "div.flex.items-center.gap-4.p-3.mb-2.border.rounded-lg"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        val chapterLink = element.selectFirst("div.flex-1 > a.font-bold")
            ?: element.selectFirst("div.flex-1 a")
        chapterLink?.let { chapter.setUrlWithoutDomain(it.attr("href")) }

        val baseName = chapterLink?.text()?.removePrefix("Leer ")?.trim() ?: ""
        val subTitle = element.selectFirst("div.flex-1 div.text-sm.font-medium")?.text()?.trim() ?: ""
        chapter.name = if (subTitle.isNotEmpty() && subTitle != baseName) "$baseName: $subTitle" else baseName

        chapter.scanlator = element.select("div.text-sm.text-right a[href*='/user/']")
            .firstOrNull()?.text()?.trim()

        val dateText = element.select("div.text-sm.text-right span.font-medium")
            .lastOrNull()?.text()?.trim()
        if (!dateText.isNullOrEmpty()) {
            chapter.date_upload = runCatching { chapterDateFormat.parse(dateText)?.time ?: 0L }.getOrDefault(0L)
        }

        return chapter
    }

    // ── Pages ────────────────────────────────────────────────────────────────
    override fun pageListParse(document: Document): List<Page> {
        // Estrategia 1: JSON embebido → const pageUrls = {"1":"url",...};
        document.select("script").map { it.data() }
            .firstOrNull { it.contains("pageUrls") }
            ?.let { script ->
                val json = Regex("""const pageUrls\s*=\s*(\{[^;]+\})""")
                    .find(script)?.groupValues?.get(1)
                if (json != null) {
                    val pages = Regex(""""(\d+)"\s*:\s*"([^"]+)"""")
                        .findAll(json)
                        .map { Page(it.groupValues[1].toInt() - 1, "", it.groupValues[2].replace("\\/", "/")) }
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
                    Page(idx, "", img.attr("abs:src").ifEmpty { img.attr("src") })
                }
            }

        // Estrategia 3: single page mode
        return document.select("div.single-page-mode img.manga-image")
            .mapIndexed { idx, img ->
                Page(idx, "", img.attr("abs:src").ifEmpty { img.attr("src") }.trim())
            }
    }

    // ── Filters ──────────────────────────────────────────────────────────────
    override fun getFilterList() = FilterList(
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

    // Géneros/categorías → /lista-manga-hentai/category/{slug}
    class GenreFilter :
        UriPartFilter(
            "Género",
            arrayOf(
                Pair("<todos>", ""),
                Pair("Ahegao", "ahegao"),
                Pair("Anal", "anal"),
                Pair("Bikini", "bikini"),
                Pair("Casadas", "casadas"),
                Pair("Chica Con Pene", "chica-con-pene"),
                Pair("Cosplay", "cosplay"),
                Pair("Doble Penetracion", "doble-penetracion"),
                Pair("Ecchi", "ecchi"),
                Pair("Embarazada", "embarazada"),
                Pair("Enfermera", "enfermera"),
                Pair("Escolares", "escolares"),
                Pair("Full Color", "full-colo"),
                Pair("Futanari", "futanari"),
                Pair("Grandes Pechos", "grandes-pechos"),
                Pair("Harem", "harem"),
                Pair("Incesto", "incesto"),
                Pair("Interracial", "interracial"),
                Pair("Juguetes Sexuales", "juguetes-sexuales"),
                Pair("Lolicon", "lolicon"),
                Pair("Maduras", "maduras"),
                Pair("Mamadas", "mamadas"),
                Pair("Masturbacion", "masturbacion"),
                Pair("MILF", "milf"),
                Pair("Orgias", "orgias"),
                Pair("Profesores", "profesores"),
                Pair("Romance", "romance"),
                Pair("Shota", "shota"),
                Pair("Sin Censura", "sin-censura"),
                Pair("Sirvientas", "sirvientas"),
                Pair("Tentaculos", "tentaculos"),
                Pair("Tetonas", "tetonas"),
                Pair("Virgenes", "virgenes"),
                Pair("Yaoi", "yaoi"),
                Pair("Yuri", "yuri"),
            ),
        )

    // Tipo de obra → /lista-de-{doujin|manga|comic}
    // Milftoon no tiene tipo propio; se busca por artista/autor.
    class TypeFilter :
        UriPartFilter(
            "Tipo de obra",
            arrayOf(
                Pair("<todos>", ""),
                Pair("Doujin", "doujin"),
                Pair("Manga", "manga"),
                Pair("Comic", "comic"),
            ),
        )

    // Ordenación → ?orderby=  (solo cuando no hay otro filtro de ruta activo)
    class SortFilter :
        UriPartFilter(
            "Ordenar por (sin otros filtros)",
            arrayOf(
                Pair("Alfabético", "alphabet"),
                Pair("Más vistos", "views"),
                Pair("Más recientes", "last"),
            ),
        )

    // Artista → /lista-manga-hentai/artist/{nombre}
    // Soporta nombres exactos: saigado, milftoon, toono suika, etc.
    class ArtistFilter : Filter.Text("Artista (ej: saigado, milftoon)")

    // Autor → /lista-manga-hentai/author/{nombre}
    class AuthorFilter : Filter.Text("Autor (ej: horori, milftoon)")

    // Scanlator/usuario → /user/{nombre}?page=N
    // Muestra todos los aportes subidos por ese usuario.
    // El nombre es sensible a mayúsculas (ej: NekoCreme, Fritz Translations).
    class ScanlatorFilter : Filter.Text("Scanlator/usuario (ej: NekoCreme, Fritz Translations)")

    // Primera letra → /lista-manga-hentai/letra/{a-z|0}
    class LetterFilter :
        UriPartFilter(
            "Primera letra",
            arrayOf(
                Pair("<todas>", ""),
                Pair("#  (0-9)", "0"),
                Pair("A", "a"), Pair("B", "b"), Pair("C", "c"), Pair("D", "d"),
                Pair("E", "e"), Pair("F", "f"), Pair("G", "g"), Pair("H", "h"),
                Pair("I", "i"), Pair("J", "j"), Pair("K", "k"), Pair("L", "l"),
                Pair("M", "m"), Pair("N", "n"), Pair("Ñ", "n"), Pair("O", "o"),
                Pair("P", "p"), Pair("Q", "q"), Pair("R", "r"), Pair("S", "s"),
                Pair("T", "t"), Pair("U", "u"), Pair("V", "v"), Pair("W", "w"),
                Pair("X", "x"), Pair("Y", "y"), Pair("Z", "z"),
            ),
        )
}
