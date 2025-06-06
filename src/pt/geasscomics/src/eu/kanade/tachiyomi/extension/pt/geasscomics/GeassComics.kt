package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
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

class GeassComics : HttpSource() {

    override val name: String = "Geass Comics"

    override val baseUrl: String = "https://geasscomics.xyz"

    private val apiUrl = "https://api.${baseUrl.substringAfterLast("/")}"

    override val lang: String = "pt-BR"

    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 2, 1)
        .build()

    // ========================= Popular ====================================

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/obras?page=$page&limit=12", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<PopularDto>()
        return MangasPage(dto.mangas.map(MangaDto::toSManga), dto.hasNextPage())
    }

    // ========================= Latest =====================================

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/capitulos/recentes?page=$page&limit=150", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<LatestDto>()
        return MangasPage(dto.mangas.map(SimpleMangaDto::toSManga).distinctBy(SManga::url), false)
    }

    // ========================= Search =====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "12")

        if (query.isNotBlank()) {
            url.addPathSegment("search")
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genres = filter.state.filter(GenreCheckBox::state)
                    if (genres.isEmpty()) return@forEach
                    url.addQueryParameter("generos", genres.joinToString { it.id })
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ========================= Details ====================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$apiUrl/obras/${manga.url}/info", headers)

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<DetailsDto>().manga.toSManga()

    // ========================= Chapters ===================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<DetailsDto>().toChapters()

    // ========================= Pages ======================================

    override fun pageListRequest(chapter: SChapter) =
        GET("$apiUrl${chapter.url.replace("manga", "obras").replace("chapter", "capitulos")}", headers)

    override fun pageListParse(response: Response): List<Page> =
        response.parseAs<PageDto>().toPages()

    override fun imageUrlParse(response: Response): String = ""

    // ========================= Filters ====================================

    override fun getFilterList(): FilterList {
        return FilterList(
            listOf(
                GenreList(
                    title = "Gêneros",
                    genres = genresList.sortedBy(Genre::name),
                ),
            ),
        )
    }
}
