package eu.kanade.tachiyomi.extension.es.minuscreators

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MinusCreators : KeiSource() {

    // The server answers 500 to bursts of about 20 requests, pages and API alike
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 10, period = 1.seconds) { it.host == siteUrl.host }

    private val siteUrl get() = baseUrl.toHttpUrl()

    private val apiUrl get() = siteUrl.resolve("/api/")!!

    override suspend fun getPopularManga(page: Int) = searchWorks(page) { addQueryParameter("orden", "populares") }

    // The site only lists the works of its last dozen chapters
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment("capitulos_recientes.php")
            .addQueryParameter("contexto", "home")
            .addQueryParameter("idiomaInterfaz", LANGUAGE)
            .build()
        val works = client.get(url, Fetch.XHR).parseAs<RecentDto>().items.filter { it.isComic }
        return MangasPage(works.map { it.toSManga(siteUrl) }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchWorks(page) {
        addQueryParameter("orden", filters.firstInstanceOrNull<SortFilter>()?.value ?: "populares")
        if (query.isNotBlank()) addQueryParameter("buscar", query.trim())
        filters.firstInstanceOrNull<TypeFilter>()?.value?.let { addQueryParameter("tipo", it) }
        filters.firstInstanceOrNull<GenreFilter>()?.value?.let { addQueryParameter("genero", it) }
        filters.firstInstanceOrNull<NsfwFilter>()?.value?.let { addQueryParameter("nsfw", it) }
    }

    // Past the last page, the API sends the last page again: its total tells where lists end
    private suspend fun searchWorks(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment("buscar.php")
            .addQueryParameter("limite", LIMIT.toString())
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("idiomaInterfaz", LANGUAGE)
            .apply(params)
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        return MangasPage(result.obras.filter { it.isComic }.map { it.toSManga(siteUrl) }, page * LIMIT < result.totalObras)
    }

    // Creator profiles and catalogue links; work and chapter links go to getMangaByUrl
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSite()) return MangasPage(emptyList(), false)
        return when (url.pathSegments[0]) {
            "perfil" -> {
                val userId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return MangasPage(emptyList(), false)
                searchWorks(page) {
                    addQueryParameter("orden", "recientes")
                    addQueryParameter("autor", userId.toString())
                }
            }
            "categorias" -> searchWorks(page) {
                addQueryParameter("orden", url.queryParameter("orden") ?: "recientes")
                url.queryParameter("buscar")?.let { addQueryParameter("buscar", it) }
                url.queryParameter("tipo")?.let { addQueryParameter("tipo", it) }
                (url.queryParameter("categoria") ?: url.queryParameter("genero"))?.let {
                    if (it == "nsfw") addQueryParameter("nsfw", "solo-nsfw") else addQueryParameter("genero", it)
                }
                url.queryParameter("nsfw")?.let { addQueryParameter("nsfw", it) }
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull()
        if (!url.isSite() || url.pathSegments[0] != "obra" || id == null) return null
        val work = getWork(id.toString())
        if (!work.isComic) return null
        return work.toSManga(siteUrl).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val work = getWork(manga.url.substringAfterLast('/'))
        return SMangaUpdate(work.toSManga(siteUrl), work.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (workId, number) = chapter.url.removePrefix("/obra/").split("/capitulo/")
        val url = apiUrl.newBuilder()
            .addPathSegment("obra_detalle.php")
            .addQueryParameter("id", workId)
            .addQueryParameter("capitulo", number)
            .addQueryParameter("idioma", LANGUAGE)
            .build()
        return client.get(url, Fetch.XHR.from(getChapterUrl(chapter))).parseAs<ReaderDto>().toPages(siteUrl)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private suspend fun getWork(id: String): WorkDetailDto {
        val url = apiUrl.newBuilder()
            .addPathSegment("obra_preview.php")
            .addQueryParameter("id", id)
            .addQueryParameter("idioma", LANGUAGE)
            .build()
        return client.get(url, Fetch.XHR).parseAs<WorkDetailsDto>().obra
    }

    private fun HttpUrl.isSite() = host.removePrefix("www.") == siteUrl.host

    companion object {
        private const val LIMIT = 50
        private const val LANGUAGE = "ES"
    }
}
