package eu.kanade.tachiyomi.extension.fr.aniverse

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Aniverse : KeiSource() {

    // The site only exposes one listing, ordered by latest chapter release.
    override val supportsLatest get() = false

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, genre = null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return fetchMangaList(page, filters.firstInstance<GenreFilter>().value)
        }

        val url = "$baseUrl/api/anime/quicksearch".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("media", "manga")
            .build()
        val mangas = client.get(url).parseAs<List<MangaItemDto>>().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private suspend fun fetchMangaList(page: Int, genre: String?): MangasPage {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply { if (genre != null) addQueryParameter("genre", genre) }
            .build()
        val dto = client.get(url).parseAs<MangaListDto>()
        return MangasPage(dto.items.map { it.toSManga() }, dto.hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = fetchMangaPage(manga.url)
        return SMangaUpdate(
            manga = dto.manga.toSManga(),
            chapters = dto.chapters
                .filterNot { it.isLocked }
                .map { it.toSChapter(dto.manga.slug) }
                .reversed(),
        )
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    private suspend fun fetchMangaPage(slug: String): MangaPageDto = client.get("$baseUrl/manga/$slug").extractNextJs<MangaPageDto>()
        ?: throw Exception("Impossible d'extraire les données du manga")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() !in listOf("manga", "read")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return fetchMangaPage(slug).manga.toSManga()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        // Locked chapters redirect to the sign-in page.
        if (response.request.url.encodedPath.startsWith("/sign-in")) {
            response.close()
            return emptyList()
        }

        val reader = response.extractNextJs<ReaderDto>()
            ?: throw Exception("Impossible d'extraire la liste des pages")
        if (reader.locked) return emptyList()

        return reader.pages.mapIndexed { index, page -> Page(index, imageUrl = page.src) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Le filtre de genre est ignoré lors d'une recherche textuelle."),
        GenreFilter(),
    )
}
