package eu.kanade.tachiyomi.extension.tr.mangatepesi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangaTepesi : KeiSource() {

    private val apiUrl get() = baseUrl.replaceFirst("://", "://api.")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("azalan-siralama", "tum-turler", "")

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val home = client.get("$apiUrl/api/home", Fetch.XHR).parseAs<HomeDto>()
        val mangas = home.lastFifteenChapters.map { it.manga.toSManga() }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "azalan-siralama"
        val type = filters.firstInstanceOrNull<TypeFilter>()?.value ?: "tum-turler"
        return getMangaList(sort, type, query)
    }

    // The whole catalog comes in one list, searched here by title
    private suspend fun getMangaList(sort: String, type: String, query: String): MangasPage {
        val url = "$apiUrl/api/mangaList".toHttpUrl().newBuilder()
            .addQueryParameter("siralama", sort)
            .addQueryParameter("tur", type)
            .build()
        val mangas = client.get(url, Fetch.XHR).parseAs<List<MangaDto>>()
            .map { it.toSManga() }
            .filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.firstOrNull() != "manga") return null
        val mangaUrl = when (segments.size) {
            3 -> "/manga/${segments[1]}/${segments[2]}"
            4 -> getChapterPages(url.encodedPath).manga.url
            else -> return null
        }
        val manga = SManga.create().apply { this.url = mangaUrl }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$apiUrl/api${manga.url}", Fetch.XHR.from(getMangaUrl(manga))).parseAs<MangaDetailsDto>()
        return SMangaUpdate(details.manga.toSMangaDetails(), details.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = getChapterPages(chapter.url).images.mapIndexed { i, image ->
        Page(i, imageUrl = image.imgUrl)
    }

    // A chapter link is /manga/<series>/<chapter>/<id>, its API path /api/manga/<series>/chapter/<chapter>/<id>
    private suspend fun getChapterPages(path: String): ChapterPagesDto {
        val (series, chapter, id) = path.removePrefix("/manga/").split("/")
        return client.get("$apiUrl/api/manga/$series/chapter/$chapter/$id", Fetch.XHR.from("$baseUrl$path")).parseAs<ChapterPagesDto>()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()
}
