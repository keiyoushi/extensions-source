package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

fun HttpUrl.Builder.addQueryParameterIfNotEmpty(name: String, value: String?): HttpUrl.Builder {
    if (!value.isNullOrEmpty()) {
        addQueryParameter(name, value)
    }
    return this
}

abstract class GreenShit(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    protected open val apiUrl = "https://api2.sussytoons.wtf"

    protected open val defaultOrderBy = "ultima_atualizacao"

    open val targetAudience: TargetAudience = TargetAudience.All

    protected open val popularGenreId = "1"

    protected open val latestGenreId = "1"

    protected open val popularType = "tipo"

    protected open val popularTypeValue = "visualizacoes_geral"

    protected open val latestEndpoint = "atualizacoes"

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "1")

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/obras/ranking".toHttpUrl().newBuilder()
            .addQueryParameter(popularType, popularTypeValue)
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "15")
            .addQueryParameter("gen_id", popularGenreId)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/$latestEndpoint".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("gen_id", latestGenreId)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("todos_generos", "1")
            .addQueryParameterIfNotEmpty("obr_nome", query)

        var orderBy = defaultOrderBy
        var orderDirection = "DESC"

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> orderBy = filter.selected
                is OrderDirectionFilter -> orderDirection = filter.selected
                is GenreFilter -> url.addQueryParameterIfNotEmpty("gen_id", filter.selected)
                is FormatFilter -> url.addQueryParameterIfNotEmpty("formt_id", filter.selected)
                is StatusFilter -> url.addQueryParameterIfNotEmpty("stt_id", filter.selected)
                is TagsFilter -> filter.state.filter { it.state }.forEach { tag ->
                    url.addQueryParameter("tags[]", tag.id)
                }
                else -> {}
            }
        }

        return GET(
            url.addQueryParameter("orderBy", orderBy)
                .addQueryParameter("orderDirection", orderDirection)
                .build(),
            headers,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Ordenar por"),
        OrderByFilter(),
        OrderDirectionFilter(),
        Filter.Separator(),
        Filter.Header("Filtros"),
        GenreFilter(getGenres()),
        FormatFilter(getFormats()),
        StatusFilter(getStatuses()),
        Filter.Separator(),
        Filter.Header("Tags"),
        TagsFilter(getTags()),
    )

    private var filters: FiltersDto? = null

    protected open fun getFilters(): FiltersDto = runBlocking {
        withContext(Dispatchers.IO) {
            filters ?: runCatching {
                client.newCall(GET("$apiUrl/obras/filtros", headers)).execute()
                    .parseAs<FiltersDto>()
            }.getOrDefault(FiltersDto()).also { filters = it }
        }
    }

    protected open fun getGenres(): List<Genre> = getFilters().genres

    protected open fun getFormats(): List<Format> = getFilters().formats

    protected open fun getStatuses(): List<Status> = getFilters().statuses

    protected open fun getTags(): List<TagCheckBox> = getFilters().tags.map { TagCheckBox(it.name, it.id.toString()) }

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = GET("$apiUrl/obras/${manga.url.removePrefix("/obra/")}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDto>()
        return dto.toSManga()
    }

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) = response.parseAs<MangaDto>().toSChapterList()

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = GET("$apiUrl/capitulos/${chapter.url.removePrefix("/capitulo/")}", headers)

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterPageDto>().toPageList()

    override fun imageUrlParse(response: Response) = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Constants ================================

    enum class TargetAudience(val value: Int) {
        All(1),
        Shoujo(4),
        Yaoi(7),
        ;

        override fun toString() = value.toString()
    }

    // ============================= Filters ================================

    protected open class SelectFilter(
        name: String,
        val options: Array<Pair<String, String>>,
        default: String? = null,
    ) : Filter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
    ) {
        val selected get() = options[state].second
    }

    protected class OrderByFilter : SelectFilter(
        "Ordenar por",
        arrayOf(
            Pair("Padrão", ""),
            Pair("Última atualização", "ultima_atualizacao"),
            Pair("Data", "data"),
            Pair("Nome A-Z", "nome_az"),
            Pair("Nome Z-A", "nome_za"),
            Pair("Visualizações", "visualizacoes_geral"),
        ),
    )

    protected class OrderDirectionFilter : SelectFilter(
        "Direção",
        arrayOf(
            Pair("Decrescente", "DESC"),
            Pair("Crescente", "ASC"),
        ),
    )

    protected class GenreFilter(genres: List<Genre>) : SelectFilter(
        "Gênero",
        arrayOf(Pair("Todos", "")) + genres.map { Pair(it.name, it.id.toString()) }.toTypedArray(),
    )

    protected class FormatFilter(formats: List<Format>) : SelectFilter(
        "Formato",
        arrayOf(Pair("Todos", "")) + formats.map { Pair(it.name, it.id.toString()) }.toTypedArray(),
    )

    protected class StatusFilter(statuses: List<Status>) : SelectFilter(
        "Status",
        arrayOf(Pair("Todos", "")) + statuses.map { Pair(it.name, it.id.toString()) }.toTypedArray(),
    )

    protected class TagsFilter(tags: List<TagCheckBox>) : Filter.Group<TagCheckBox>(
        "Tags",
        tags,
    )

    protected class TagCheckBox(
        name: String,
        val id: String,
    ) : Filter.CheckBox(name)

    companion object {
    }
}
