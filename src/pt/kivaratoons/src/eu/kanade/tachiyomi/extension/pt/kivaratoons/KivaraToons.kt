package eu.kanade.tachiyomi.extension.pt.kivaratoons

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class KivaraToons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val siteUrl get() = baseUrl.toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, defaultSort = "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/leitores/ultimas-atualizacoes?$SHOW_ADULT_CONTENT".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", UPDATES_PAGE_SIZE.toString())
            .build()

        val result = client.get(url).parseAs<LatestUpdatesDto>()
        return MangasPage(result.mangas.map { it.toSManga(siteUrl) }, result.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters, defaultSort = "recentes")

    private suspend fun getMangaList(
        page: Int,
        query: String = "",
        filters: FilterList = FilterList(),
        defaultSort: String,
    ): MangasPage {
        val url = "$baseUrl/api/obras?$SHOW_ADULT_CONTENT".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", MANGA_PAGE_SIZE.toString())
            .addQueryParameter("ordem", filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: defaultSort)
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("busca", query)
                }
                filters.filterIsInstance<UrlFilter>()
                    .filterNot { it is SortFilter }
                    .forEach { it.addToUrl(this) }
            }
            .build()

        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.mangas.map { it.toSManga(siteUrl) }, result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != siteUrl.host || url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val mangaId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = mangaId.toString() }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$baseUrl/api/obras/${manga.url}?$SHOW_ADULT_CONTENT").parseAs<MangaDto>()

        return SMangaUpdate(
            manga = details.toSManga(siteUrl),
            chapters = details.chapters.map(ChapterDto::toSChapter),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl/api/capitulos/${chapter.url}?$SHOW_ADULT_CONTENT")
        .parseAs<ChapterPagesDto>()
        .toPageList(siteUrl)

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val formats = async {
            client.get("$baseUrl/api/filtros/formatos").parseAs<FormatListDto>().formats
        }
        val statuses = async {
            client.get("$baseUrl/api/filtros/status").parseAs<StatusListDto>().status
        }
        val tags = async {
            client.get("$baseUrl/api/filtros/tags").parseAs<TagListDto>().tags
        }

        FilterData(formats.await(), statuses.await(), tags.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList(SortFilter())

        return FilterList(
            SortFilter(),
            FormatFilter(filterData.formats),
            StatusFilter(filterData.statuses),
            GenreFilter(filterData.tags),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/obra/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.memo["mangaId"]!!.int}/${chapter.url}"

    companion object {
        private const val MANGA_PAGE_SIZE = 24
        private const val UPDATES_PAGE_SIZE = MANGA_PAGE_SIZE * 2
        private const val SHOW_ADULT_CONTENT = "mostrar_conteudo_adulto=true"
        private val MANGA_PATH_SEGMENTS = listOf("obra", "manhwa", "reader")
    }
}
