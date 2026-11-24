package eu.kanade.tachiyomi.extension.pt.superecchi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SuperEcchi : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    // The site only publishes a "latest releases" listing, so it is served here.
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/index.php?pageId=$page", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(LISTING_SELECTOR).map(::parseListingEntry)
        val hasNextPage = document.selectFirst(".pagination a[href*=pageId=${page + 1}]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = coroutineScope {
        if (query.isBlank()) return@coroutineScope MangasPage(emptyList(), false)
        val body = FormBody.Builder().add("id", query).build()
        val suggestions = client.post("$baseUrl/ajax_pesquisar.php", Fetch.XHR, body).parseAs<List<String>?>().orEmpty()
        val mangas = suggestions.map { async { suggestionResults(it) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.url }
        MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.encodedPath !in MANGA_PATHS) return null
        val slug = url.queryParameter("t")?.takeIf { it.isNotBlank() } ?: return null
        return SManga.create().apply {
            this.url = "livro.php?t=$slug"
            setDetails(client.get(getMangaUrl(this), Fetch.DOCUMENT).asJsoup())
            initialized = true
        }
    }

    private suspend fun suggestionResults(suggestion: String): List<SManga> {
        val kind = SUGGESTION_KIND_REGEX.find(suggestion)?.groupValues?.get(1) ?: return emptyList()
        val name = suggestion.substringBeforeLast(" (").trim()
        if (name.isEmpty()) return emptyList()
        val seo = nomeSeo(name)
        return when (kind) {
            "capitulo" -> listOf(
                SManga.create().apply {
                    url = "livro.php?t=$seo-1"
                    title = name
                },
            )
            "tag" -> fetchListingEntries("$baseUrl/tag.php?tag=$seo")
            "serie" -> fetchListingEntries("$baseUrl/serie.php?serie=$seo")
            "autor" -> fetchListingEntries("$baseUrl/autor.php?autor=$seo")
            "personagem" -> fetchListingEntries("$baseUrl/personagem.php?personagem=$seo")
            "tipo" -> fetchListingEntries("$baseUrl/tipo.php?tipo=$seo")
            else -> emptyList()
        }
    }

    private suspend fun fetchListingEntries(url: String): List<SManga> = client.get(url, Fetch.DOCUMENT).asJsoup().select(LISTING_SELECTOR).map(::parseListingEntry)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.setDetails(document)
        return SMangaUpdate(manga, parseChapters(document))
    }

    private fun SManga.setDetails(document: Document) {
        val rawTitle = document.selectFirst("h1")?.text().orEmpty()
            .removePrefix("Album:").trim()
        check(rawTitle.isNotBlank()) { "Empty title for $url" }
        title = rawTitle
        author = document.detailField("Autor")
        artist = author
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".sinopse-completa")?.clone()?.apply { select("h3").remove() })
            .build()
        genre = document.tags()
        thumbnail_url = document.selectFirst(".dj-img1 img")?.attrOrNull("abs:src")
        status = if (document.detailField("Tipo")?.contains("one-shot", ignoreCase = true) == true) {
            SManga.COMPLETED
        } else {
            SManga.ONGOING
        }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val date = DATE_FORMAT.tryParseDateTime(
            document.selectFirst(".cg-date.date")?.text(),
            SITE_ZONE,
        )
        return document.select("a[href^=leitor.php?t=]")
            .map { it.attr("href") }
            .distinct()
            .mapNotNull { href ->
                val slug = href.substringAfter("t=")
                val number = slug.substringAfterLast("-").toFloatOrNull() ?: return@mapNotNull null
                SChapter.create().apply {
                    url = href
                    name = "Capítulo ${number.toInt()}"
                    chapter_number = number
                    date_upload = date
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#gerar_pdf img[src^=mangas/]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:src")) }

    private fun parseListingEntry(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h2 a.titulo-linhas")!!
        url = link.attr("href")
        val rawTitle = link.text()
        check(rawTitle.isNotBlank()) { "Empty title for entry: $url" }
        title = rawTitle
        thumbnail_url = element.selectFirst(".dj-img1 img")?.attrOrNull("abs:src")
        author = element.detailField("Autor")
        genre = element.tags()
    }

    private fun Element.detailField(label: String): String? = select("table.dj-desc tr")
        .firstOrNull { it.selectFirst("td")?.text().equals(label, ignoreCase = true) }
        ?.select("td")?.getOrNull(1)
        ?.textOrNull()

    private fun Element.tags(): String? = select("td.relatedtags a[href^=tag.php]").eachText()
        .distinct()
        .joinToString()
        .ifEmpty { null }

    private fun nomeSeo(name: String): String = name.replace("-", "*").replace(" ", "-")

    companion object {
        private const val LISTING_SELECTOR =
            ".gallery-content > div.cg, .gallery-content > div.dj, .gallery-content > div.manga, " +
                ".gallery-content > div.hq, .gallery-content > div.anime, .gallery-content > div.acg"

        private val MANGA_PATHS = setOf("/livro.php", "/leitor.php")

        private val SUGGESTION_KIND_REGEX = Regex("""\(([^)]+)\)$""")

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)
        private val SITE_ZONE = ZoneId.of("America/Sao_Paulo")
    }
}
