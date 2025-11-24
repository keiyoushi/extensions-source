package eu.kanade.tachiyomi.multisrc.greenshit

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
    protected open val cdnUrl = "https://cdn.sussytoons.wtf"
    protected open val useWidthInThumbnail = true
    protected open val defaultOrderBy = "ultima_atualizacao"
    open val targetAudience: TargetAudience = TargetAudience.All
    protected open val popularGenreId = "1"
    protected open val latestGenreId = "1"
    protected open val popularType = "tipo"
    protected open val popularTypeValue = "visualizacoes_geral"
    protected open val latestEndpoint = "atualizacoes"
    protected open val rateLimitPerSecond = 2
    protected open val includeSlugInUrl: Boolean = false
    protected open val defaultScanId: Int? = null

    private fun <T> parseJsonResponse(response: Response, serializer: kotlinx.serialization.KSerializer<T>): T {
        val jsonElement = response.parseAs<JsonElement>()
        val unwrapped = if (jsonElement is JsonObject && jsonElement.containsKey("resultado")) {
            jsonElement["resultado"]!!
        } else {
            jsonElement
        }
        return jsonInstance.decodeFromJsonElement(serializer, unwrapped)
    }

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(rateLimitPerSecond)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "1")

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int): Request =
        mangasRequest(page, "$apiUrl/obras/ranking", "15", popularGenreId) {
            addQueryParameter(popularType, popularTypeValue)
        }

    override fun popularMangaParse(response: Response) = mangasParse(response)

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request =
        mangasRequest(page, "$apiUrl/obras/$latestEndpoint", "24", latestGenreId)

    override fun latestUpdatesParse(response: Response) = mangasParse(response)

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("todos_generos", "1")
            .addQueryParameterIfNotEmpty("obr_nome", query)

        val orderBy = filters.firstInstanceOrNull<OrderByFilter>()?.selected ?: defaultOrderBy
        val orderDirection = filters.firstInstanceOrNull<OrderDirectionFilter>()?.selected ?: "DESC"

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addQueryParameterIfNotEmpty(genreFilterKey, filter.selected)
                is FormatFilter -> url.addQueryParameterIfNotEmpty(formatFilterKey, filter.selected)
                is StatusFilter -> url.addQueryParameterIfNotEmpty(statusFilterKey, filter.selected)
                is TagsFilter -> filter.state.filter { it.state }.forEach { tag ->
                    url.addQueryParameter(tagFilterKey, tag.id)
                }
                else -> {}
            }
        }

        url.addQueryParameter("orderBy", orderBy)
            .addQueryParameter("orderDirection", orderDirection)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = mangasParse(response)

    private fun mangasRequest(
        page: Int,
        url: String,
        limit: String,
        genreId: String,
        block: (HttpUrl.Builder.() -> Unit)? = null,
    ): Request {
        val httpUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", limit)
            .addQueryParameter("gen_id", genreId)

        block?.invoke(httpUrl)

        return GET(httpUrl.build(), headers)
    }

    private fun mangasParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(cdnUrl, useWidthInThumbnail, includeSlugInUrl, defaultScanId), dto.hasNextPage())
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

    protected open fun getFilters(): FiltersDto = filters ?: runBlocking(Dispatchers.IO) {
        runCatching {
            client.newCall(GET("$apiUrl/obras/filtros", headers)).execute()
                .parseAs<FiltersDto>()
        }.getOrDefault(FiltersDto())
    }.also { filters = it }

    protected open fun getGenres(): List<Genre> = getFilters().genres
    protected open fun getFormats(): List<Format> = getFilters().formats
    protected open fun getStatuses(): List<Status> = getFilters().statuses
    protected open fun getTags(): List<TagCheckBox> = getFilters().tags.map { TagCheckBox(it.name, it.id.toString()) }
    protected open val genreFilterKey = "gen_id"
    protected open val formatFilterKey = "formt_id"
    protected open val statusFilterKey = "stt_id"
    protected open val tagFilterKey = "tags[]"

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfter("/obra/").substringBefore("/")
        return GET("$apiUrl/obras/$mangaId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDto = parseJsonResponse(response, MangaDto.serializer())
        return mangaDto.toSManga(cdnUrl, useWidthInThumbnail, includeSlugInUrl, defaultScanId)
    }

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDto = parseJsonResponse(response, MangaDto.serializer())
        return mangaDto.toSChapterList()
    }

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = GET("$apiUrl/capitulos/${chapter.url.removePrefix("/capitulo/")}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pagesDto = parseJsonResponse(response, ChapterPagesDto.serializer())
        return pagesDto.toPageList(cdnUrl)
    }

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
            Pair("Mais Visualizações", "visualizacoes_geral"),
            Pair("Lançamentos", "criacao"),
            Pair("Melhor Avaliação", "rating"),
            Pair("Nome", "nome"),
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
}
