package eu.kanade.tachiyomi.extension.pt.fenixproject

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FenixProject : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = fetchListing("$baseUrl/manhwas?pagina=$page")

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/pesquisar".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("pagina", page.toString())
                .build()
            return fetchListing(url.toString())
        }

        val genreId = filters.firstInstanceOrNull<Filters>()?.genreId
        return if (genreId != null) {
            fetchListing("$baseUrl/manhwas?genero=$genreId&pagina=$page")
        } else {
            fetchListing("$baseUrl/manhwas?pagina=$page")
        }
    }

    private suspend fun fetchListing(url: String): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("#works-grid > div").mapNotNull(::parseCard)
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun parseCard(card: Element): SManga? {
        val link = card.selectFirst("a[href^='/manga/']") ?: return null
        val slug = link.attr("href").removePrefix("/manga/").trim('/')
        val title = card.selectFirst("h3")?.text()?.ifBlank { null }
            ?: card.selectFirst("img")?.attr("alt")?.trim()?.ifBlank { null }
            ?: return null
        if (slug.isBlank()) return null
        return SManga.create().apply {
            url = slug
            this.title = title
            thumbnail_url = card.selectFirst("img")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return getMangaUpdate(
            manga = SManga.create().apply { this.url = slug },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = manga.apply {
            document.selectFirst("main h1")?.text()?.let { title = it }
            thumbnail_url = document.selectFirst("main img[alt]:not([alt=''])")?.absUrl("src")
            val fields = document.detailFields()
            author = fields["Autor"]?.takeUnless { it == "—" }
            artist = fields["Artista"]?.takeUnless { it == "—" }
            status = parseStatus(fields["Status"])
            genre = document.select("a[href*='genero=']").joinToString { it.text() }.ifEmpty { null }
            description = document.select("h2").firstOrNull { it.text() == "Sinopse" }
                ?.nextElementSibling()?.text()
        }

        if (!fetchChapters) return SMangaUpdate(details, chapters)
        return SMangaUpdate(details, parseChapters(document))
    }

    private fun Document.detailFields(): Map<String, String> = select("dl dt").associate { dt ->
        dt.text() to (dt.nextElementSibling()?.text() ?: "")
    }

    private fun parseStatus(status: String?): Int {
        val value = status?.lowercase().orEmpty()
        return when {
            "andamento" in value -> SManga.ONGOING
            "conclu" in value -> SManga.COMPLETED
            "hiato" in value -> SManga.ON_HIATUS
            "cancel" in value -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun parseChapters(document: Document): List<SChapter> {
        val cards = document.select("div[id^='chapter-card-']")
        // The detail page renders at most the 10 latest chapters; the full list is in the
        // chapter page's selector.
        if (cards.size < DETAIL_CHAPTER_LIMIT) return cards.mapNotNull(::parseDetailChapter)

        val dates = cards.mapNotNull { card ->
            val href = card.selectFirst("a[href*='/capitulo-']")?.attr("href") ?: return@mapNotNull null
            val dateText = card.select("span").firstOrNull { DATE_REGEX.matches(it.text()) }?.text()
            dateText?.let { href to DATE_FORMAT.tryParseDate(it, SITE_ZONE) }
        }.toMap()

        val latestChapterUrl = cards.first()?.selectFirst("a[href*='/capitulo-']")?.absUrl("href")
            ?: return cards.mapNotNull(::parseDetailChapter)

        val fullList = client.get(latestChapterUrl).asJsoup()
            .select("#chapter-select-top a[href*='/capitulo-']")
            .map { link ->
                val href = link.attr("href")
                val number = chapterNumber(href)
                SChapter.create().apply {
                    url = href
                    name = link.text().ifBlank { number?.let { "Capítulo ${it.format()}" } ?: "Capítulo" }
                    if (number != null) chapter_number = number
                    date_upload = dates[href] ?: 0L
                }
            }

        return (fullList.ifEmpty { cards.mapNotNull(::parseDetailChapter) })
            .sortedByDescending { it.chapter_number }
    }

    private fun parseDetailChapter(card: Element): SChapter? {
        val href = card.selectFirst("a[href*='/capitulo-']")?.attr("href") ?: return null
        val number = chapterNumber(href)
        val dateText = card.select("span").firstOrNull { DATE_REGEX.matches(it.text()) }?.text()
        return SChapter.create().apply {
            url = href
            name = card.selectFirst("span[title]")?.text()?.ifBlank { null }
                ?: number?.let { "Capítulo ${it.format()}" } ?: "Capítulo"
            if (number != null) chapter_number = number
            date_upload = DATE_FORMAT.tryParseDate(dateText, SITE_ZONE)
        }
    }

    private fun chapterNumber(href: String): Float? = CHAPTER_NUMBER_REGEX.find(href)?.groupValues?.get(1)?.toFloatOrNull()

    private fun Float.format(): String = toString().removeSuffix(".0")

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("img[alt^='Página ']")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("O gênero é ignorado ao buscar por texto"),
        Filters(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    companion object {
        private const val DETAIL_CHAPTER_LIMIT = 10

        private val CHAPTER_NUMBER_REGEX = Regex("""capitulo-(\d+(?:\.\d+)?)""")
        private val DATE_REGEX = Regex("""\d{2}/\d{2}/\d{4}""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
        private val SITE_ZONE = ZoneId.of("America/Sao_Paulo")
    }
}
