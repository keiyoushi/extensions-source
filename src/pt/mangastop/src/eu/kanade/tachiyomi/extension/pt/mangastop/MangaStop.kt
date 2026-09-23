package eu.kanade.tachiyomi.extension.pt.mangastop

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@Source
abstract class MangaStop : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(2)
    }

    private val apiUrl: HttpUrl get() = "$baseUrl/wp-json/mangastop/v1".toHttpUrl()

    override suspend fun getPopularManga(page: Int) = fetchMangaList("mais-populares", page)

    override suspend fun getLatestUpdates(page: Int) = fetchMangaList("recentes", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        query.toSiteUrlOrNull()?.let { return getMangasByUrl(it, page) }

        if (query.isNotBlank()) {
            val url = apiUrl.newBuilder()
                .addPathSegment("busca")
                .addQueryParameter("q", query.trim())
                .addQueryParameter("tipo", "obras")
                .addQueryParameter("pagina", page.toString())
                .addQueryParameter("por_pagina", PER_PAGE)
                .build()
            return client.get(url).parseAs<BuscaDto>().toMangasPage()
        }

        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        if (genre.isNotEmpty()) {
            return fetchMangaList("genero", page, type) { addQueryParameter("slug", genre) }
        }

        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: "mais-populares"
        return fetchMangaList(sort, page, type)
    }

    private suspend fun fetchMangaList(
        endpoint: String,
        page: Int,
        type: String = "",
        params: HttpUrl.Builder.() -> Unit = {},
    ): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment(endpoint)
            .apply(params)
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("por_pagina", PER_PAGE)
            .apply { if (type.isNotEmpty()) addQueryParameter("tipo", type) }
            .build()
        return client.get(url).parseAs<MangaListDto>().toMangasPage()
    }

    // Also accepts site links pasted without the scheme, e.g. "mangastop.net/obra/123" or "/manga/slug/"
    private fun String.toSiteUrlOrNull(): HttpUrl? {
        val query = trim()
        val host = baseUrl.toHttpUrl().host
        return when {
            query.startsWith("/") -> "$baseUrl$query"
            query.startsWith("$host/") || query.startsWith("www.$host/") -> "https://$query"
            else -> null
        }?.toHttpUrlOrNull()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val id = resolveId(url.encodedPath) ?: return null
        val mangaId = when (url.pathSegments.first()) {
            "obra", "manga" -> id
            else -> fetchLeitor(id).mangaId.toString()
        }
        return fetchObra(mangaId).toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.memo["id"]?.stringOrNull ?: return baseUrl + manga.url
        return "$baseUrl/obra/$id"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = resolveId(manga.url) ?: throw Exception("Obra não encontrada")
        val obra = fetchObra(id)
        return SMangaUpdate(obra.toSManga(), obra.toSChapterList())
    }

    private suspend fun fetchObra(id: String) = client.get(apiUrl.newBuilder().addPathSegment("obra").addPathSegment(id).build())
        .parseAs<ObraDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = resolveId(chapter.url) ?: throw Exception("Capítulo não encontrado")
        return fetchLeitor(id).imagens.mapIndexed { index, imagem -> Page(index, imageUrl = imagem.url) }
    }

    private suspend fun fetchLeitor(id: String) = client.get(apiUrl.newBuilder().addPathSegment("leitor").addPathSegment(id).build())
        .parseAs<LeitorDto>()

    /**
     * Returns the WordPress post id behind a site path. Current paths (/obra/{id}, /leitor/{id}) carry it
     * directly; paths from the old MangaThemesia site (/manga/{slug}/ and /{chapter-slug}/) are looked up by slug.
     */
    private suspend fun resolveId(path: String): String? {
        val segments = path.trim('/').split('/')
        val (type, slug) = when {
            segments.size >= 2 && segments[0] in listOf("obra", "leitor") -> return segments[1].toLongOrNull()?.toString()
            segments.size >= 2 && segments[0] == "manga" -> "manga" to segments[1]
            segments.size == 1 && segments[0].isNotEmpty() -> "posts" to segments[0]
            else -> return null
        }
        val url = "$baseUrl/wp-json/wp/v2".toHttpUrl().newBuilder()
            .addPathSegment(type)
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", "id")
            .build()
        return client.get(url).parseAs<List<PostIdDto>>().firstOrNull()?.id?.toString()
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = mutableListOf<GenreDto>()
        var page = 1
        do {
            val url = "$baseUrl/wp-json/wp/v2/genres".toHttpUrl().newBuilder()
                .addQueryParameter("per_page", "100")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("hide_empty", "true")
                .addQueryParameter("_fields", "name,slug")
                .build()
            val response = client.get(url)
            val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
            genres += response.parseAs<List<GenreDto>>()
        } while (page++ < totalPages)
        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
        return FilterList(
            buildList<Filter<*>> {
                add(Filter.Header("Os filtros não se aplicam à busca por texto"))
                add(SortFilter())
                add(TypeFilter())
                if (genres != null) {
                    add(Filter.Header("Com um gênero selecionado, a ordenação é ignorada"))
                    add(GenreFilter(genres))
                }
            },
        )
    }

    companion object {
        private const val PER_PAGE = "24"
    }
}
