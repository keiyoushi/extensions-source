package eu.kanade.tachiyomi.extension.pt.shiraiscans
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ShiraiScans : KeiSource() {

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = parseLatest(client.get(baseUrl))

    private fun parseLatest(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("section.atualizacoes .manga-card, section#secao-atualizacoes .manga-card").map {
            SManga.create().apply {
                val onClick = it.attr("onclick")
                url = "/" + onClick.substringAfter("href='").substringBefore("'")
                title = it.selectFirst(".manga-title")!!.text()
                thumbnail_url = it.selectFirst(".manga-cover")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: "todos"
        val offset = (page - 1) * 15

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("biblioteca.php")
            addQueryParameter("ajax", "true")
            addQueryParameter("genero", genre)
            addQueryParameter("q", query)
            addQueryParameter("offset", offset.toString())
        }.build()

        return parseSearch(client.get(url))
    }

    private fun parseSearch(response: Response): MangasPage {
        val document = response.asJsoup()
        val cards = document.select(".library-card")

        val mangas = cards.map {
            SManga.create().apply {
                val onClick = it.attr("onclick")
                url = "/" + onClick.substringAfter("href='").substringBefore("'")
                title = it.selectFirst(".library-title")!!.text()
                thumbnail_url = it.selectFirst(".library-cover")?.absUrl("src")
            }
        }

        return MangasPage(mangas, cards.size == 15)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            parseDetails(doc),
            parseChapters(doc),
        )
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst(".obra-titulo")!!.text()
        thumbnail_url = document.selectFirst(".obra-capa-grande")?.absUrl("src")
        author = document.selectFirst(".info-linha:contains(Autor) span:last-child")?.text()?.takeIf { it != "?" }
        artist = document.selectFirst(".info-linha:contains(Artista) span:last-child")?.text()?.takeIf { it != "?" }
        description = document.selectFirst(".obra-sinopse")?.text()
        genre = document.select(".obra-generos .genero-badge").joinToString { it.text().removePrefix("#") }

        val statusText = document.selectFirst(".info-linha:contains(Status) span:last-child")?.text()
        status = when {
            statusText == null -> SManga.UNKNOWN
            statusText.contains("Lançamento", true) -> SManga.ONGOING
            statusText.contains("Completo", true) -> SManga.COMPLETED
            statusText.contains("Hiato", true) -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters ==============================

    private fun parseChapters(document: Document) = document.select(".lista-capitulos .capitulo-item").map {
        SChapter.create().apply {
            url = "/" + it.attr("href")
            name = it.selectFirst(".capitulo-title")!!.text().replace("NOVO", "").trim()
            date_upload = it.selectFirst(".capitulo-date")?.text()?.let { dateStr ->
                dateFormat.tryParseDate(dateStr)
            } ?: 0L
        }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val script = document.selectFirst("script:containsData(pagesData)")?.data()
            ?: throw Exception("Script com dados das páginas não encontrado")

        val jsonString = script.substringAfter("const pagesData = ").substringBefore(";")
        val pages = jsonString.parseAs<List<Dto>>()

        return pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.url)
        }
    }
    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
    )

    companion object {
        private val dateFormat =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    }
}
