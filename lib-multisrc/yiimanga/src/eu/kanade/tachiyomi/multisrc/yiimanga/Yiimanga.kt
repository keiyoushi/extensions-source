package eu.kanade.tachiyomi.multisrc.yiimanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

abstract class Yiimanga : KeiSource() {

    protected abstract val mangaPath: String

    protected open val searchParam = "s"

    // Catalog route the site sorts with ?order=views
    protected open val popularPath: String? = null

    override val supportsLatest get() = popularPath != null

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val path = popularPath ?: return getLatestUpdates(page)
        val url = listUrl(path, page = page).addQueryParameter("order", "views").build()
        return mangaListParse(client.get(url).asJsoup())
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get(listUrl(page = page).build()).asJsoup())

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("search/")
                .addQueryParameter(searchParam, query)
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug.orEmpty()
            if (genre.isEmpty()) return getLatestUpdates(page)
            genreUrl(genre, page)
        }
        return mangaListParse(client.get(url).asJsoup())
    }

    protected open fun genreUrl(genre: String, page: Int): HttpUrl = listUrl("genre", genre, page = page).build()

    protected fun listUrl(vararg segments: String, page: Int): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
        addPathSegment("page")
        addPathSegment(page.toString())
        addPathSegment("")
    }

    private fun mangaListParse(document: Document) = MangasPage(
        document.select(".latest-list .latest-item, .list .list-item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".latest-right a:has(h3, h4), .item-title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst(".latest-left img, .item-img img")?.absUrl("src")
            }
        },
        hasNextPage = document.selectFirst("#pagination li.next a") != null,
    )

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != mangaPath) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/$mangaPath/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.main-info-title, .post-title h1, .manga-detail .info h1")!!.text()
        thumbnail_url = document.selectFirst("img.img-cover, .summary_image img, .manga-detail .poster img")?.absUrl("src")
        author = document.info("Author(s)", "Author", "Autores")?.names()
        artist = document.info("Artist(s)", "Artist", "Artistas")?.names()
        genre = document.info("Genres", "Genre(s)", "Géneros")?.names()
        val statusText = document.info("Status", "Estado") ?: document.selectFirst(".manga-detail .info p.type")
        status = when (statusText?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val synopsis = document.selectFirst(".short-desc-content, .dsct, .description-summary")?.let { block ->
            block.select("p, li").ifEmpty { listOf(block) }
                .mapNotNull { it.textOrNull() }
                .joinToString("\n\n")
                .takeIf { it.isNotEmpty() }
        }
        val altNames = document.info("Alternative")?.textOrNull()
            ?: document.selectFirst(".main-info-list li > h4, .manga-detail .info h6")?.textOrNull()
        description = listOfNotNull(
            synopsis,
            altNames?.let { "Alternative names: $it" },
        ).joinToString("\n\n").takeIf { it.isNotEmpty() }
    }

    private fun Document.info(vararg labels: String): Element? {
        val heading = select(".main-info-list li > h5, .post-content_item .summary-heading > h5, .info-item > span.sh")
            .firstOrNull { it.text().removeSuffix(":").removeSuffix(" -") in labels }
            ?: return null
        return heading.closest("li, .post-content_item, .info-item")!!.selectFirst("> :not(h5, .summary-heading, .sh)")
    }

    // Links on .info-item rows carry their own comma in a nested span
    private fun Element.names(): String? {
        val links = select("a")
        val names = if (links.isEmpty()) listOf(textOrNull()) else links.map { it.ownTextOrNull() }
        return names.filterNotNull().joinToString().takeIf { it.isNotEmpty() }
    }

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document): List<SChapter> = document.select("ul.chapter-list li, ul.row-content-chapter li, .list-items li.item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.selectFirst("a[href]")!!.absUrl("href"))
            name = element.selectFirst(".chapter-name, .item a > span:first-child")!!.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".ct-update, .chapter-time, .item a > span:nth-child(2)")?.text())
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(".reading-content img, .read-content img")
        .mapIndexed { index, element -> Page(index, imageUrl = element.absUrl("src")) }

    // =============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select(".genre-list a, a.item-nav[href*=/genre/], a.item-nav-mb[href*=/genre/]")
        .map { it.text() to it.absUrl("href").toHttpUrl().pathSegments[1] }
        .distinctBy { it.second }
        .sortedBy { it.first }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>() ?: return FilterList()
        return FilterList(
            Filter.Header("Ignored when using text search"),
            GenreFilter("Genre", listOf("All" to "") + genres),
        )
    }

    companion object {
        // "06 Oct 2026", "12 Oct 26", or month and two-digit year only: "Oct 26"
        private val dateFormat = DateTimeFormatterBuilder()
            .appendPattern("[dd ]MMM [yyyy][yy]")
            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
            .toFormatter(Locale.ENGLISH)

        // The Spanish sites list no genres
        @JvmStatic
        protected val spanishGenres = listOf(
            "Todos" to "",
            "Acción" to "accion",
            "Adulto" to "adulto",
            "Aventura" to "aventura",
            "Ciencia ficción" to "ciencia-ficcion",
            "Comedia" to "comedia",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Familia" to "familia",
            "Fantasía" to "fantasia",
            "Harem" to "harem",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Maduro" to "maduro",
            "Magia" to "magia",
            "Recuentos de la vida" to "recuentos-de-la-vida",
            "Reencarnación" to "reencarnacion",
            "Romance" to "romance",
            "Seinen" to "seinen",
            "Shonen" to "shonen",
            "Sin censura" to "sin-censura",
            "Smut" to "smut",
            "Sobrenatural" to "sobrenatural",
            "Supervivencia" to "supervivencia",
            "Vida escolar" to "vida-escolar",
        )
    }
}
