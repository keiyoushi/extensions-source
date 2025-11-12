package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

abstract class GreenShit(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    private val apiUrlV2 = "https://api2.sussytoons.wtf"

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "1")

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$API_URL_V2/obras/recentes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "18")
            .addQueryParameter("gen_id", "1")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$API_URL_V2/obras/atualizacoes".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("gen_id", "1")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ResultDto<List<MangaDto>>>()
        return MangasPage(dto.toSMangaList(), dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$API_URL_V2/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("todos_generos", "1")
            .addQueryParameterIfNotEmpty("obr_nome", query)

        var orderBy = "ultima_atualizacao"
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
        Filter.Header("Ordernar por"),
        OrderByFilter(),
        OrderDirectionFilter(),
        Filter.Separator(),
        Filter.Header("Filtros"),
        GenreFilter(),
        FormatFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Selecionar Tags"),
        TagsFilter(TAGS_LIST),
    )

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = GET("$API_URL_V2/obras/${manga.url.removePrefix("/obra/")}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDto>()
        return dto.toSManga()
    }

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) = response.parseAs<MangaDto>().toSChapterList()

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = GET("$API_URL_V2/capitulos/${chapter.url.removePrefix("/capitulo/")}", headers)

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterPageDto>().toPageList()

    override fun imageUrlParse(response: Response) = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Constants ================================

    companion object {
        private const val API_URL_V2 = "https://api2.sussytoons.wtf"
    }
}
