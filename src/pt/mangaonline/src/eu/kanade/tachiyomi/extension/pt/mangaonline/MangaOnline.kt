package eu.kanade.tachiyomi.extension.pt.mangaonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaOnline : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    private val popularFilter = SelectFilter("", arrayOf(Option("", "popular", "sort")))

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(popularFilter))

    override suspend fun getLatestUpdates(page: Int) = getMangasPage(client.get("$baseUrl/atualizacoes?page=$page").asJsoup())

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/catalogo".toHttpUrl().newBuilder()
            .addQueryParameter("perPage", "24")
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.filterIsInstance<SelectFilter>().forEach {
            val filter = it.selected()
            url.addQueryParameter(filter.param, filter.id)
        }

        filters.firstInstanceOrNull<GenreFilter>()?.state.takeUnless(String?::isNullOrBlank)?.let {
            url.addQueryParameter("genre", it)
        }

        return getMangasPage(client.get(url.build()).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val manga = SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst(".synopsis")?.text()
            genre = document.select(".tags span").joinToString { it.text() }
            document.selectFirst(".manga-info-pill-status")?.text()?.let {
                status = when (it.lowercase()) {
                    "em andamento" -> SManga.ONGOING
                    "completo" -> SManga.COMPLETED
                    "hiato" -> SManga.ON_HIATUS
                    "cancelado" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
            setUrlWithoutDomain(document.location())
        }
        val chapters = document.select(".chapter-row").map { element ->
            SChapter.create().apply {
                name = element.selectFirst(".chapter-title-line")!!.text()
                setUrlWithoutDomain(element.selectFirst(".chapter-main-link")!!.absUrl("href"))
            }
        }
        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".reader-content img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }
    protected class Option(name: String, val id: String, val param: String) : Filter.CheckBox(name)

    protected class SelectFilter(displayName: String, private val vals: Array<Option>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
        fun selected() = vals[state]
    }

    protected class GenreFilter(title: String) : Filter.Text(title)

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf(
            SelectFilter(
                displayName = "Ordenar por",
                vals = buildFilter("sort", emptyArray()) {
                    arrayOf(
                        "Mais recentes" to "recent",
                        "Mais populares" to "popular",
                        "Titulo A-Z" to "title_asc",
                        "Titulo Z-A" to "title_desc",
                    )
                },
            ),
            SelectFilter(
                displayName = "Status",
                vals = buildFilter("status") {
                    listOf("Em andamento", "Completo", "Hiato", "Cancelado")
                        .map { it to it.lowercase() }
                        .toTypedArray()
                },
            ),
            SelectFilter(
                displayName = "Tipo",
                vals = buildFilter("type") {
                    arrayOf("Manga", "Manhwa", "Manhua", "Hentai")
                        .map { it to it.lowercase() }
                        .toTypedArray()
                },
            ),
            SelectFilter(
                displayName = "Conteúdo +18",
                vals = buildFilter("adult") {
                    arrayOf("Somente +18" to "only", "Ocultar +18" to "hide")
                },
            ),
            GenreFilter("Gênero (Ex.: Acao, Romance"),
        )
        return FilterList(filters)
    }

    private fun buildFilter(
        param: String,
        defaultOptions: Array<Option> = arrayOf(Option("Todos", "all", param)),
        builder: (String) -> Array<Pair<String, String>>,
    ) = defaultOptions + builder(param).map { Option(it.first, it.second, param) }

    private fun getMangasPage(document: Document): MangasPage {
        val mangas = document.select(".manga-card > a, .latest-manga-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setOfNotNull(element.absUrl("href"), element.selectFirst("a")?.absUrl("href"))
                    .first(String::isNotBlank)
                    .let { setUrlWithoutDomain(it) }
            }
        }

        return MangasPage(mangas, document.selectFirst("a.public-page-link:contains(Proxima)") != null)
    }
}
