package eu.kanade.tachiyomi.extension.ar.arabtoons

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.intOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ArabToons : KeiSource() {

    private val apiUrl get() = "$baseUrl/api".toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page) { addQueryParameter("sort", "views_count") }

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page) {
        if (query.isNotBlank()) addQueryParameter("keyword", query)
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
    }

    private suspend fun browse(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = apiUrl.newBuilder()
            .addPathSegment("browse")
            .addQueryParameter("page", page.toString())
            .apply(params)
            .build()
        val data = client.get(url).parseAs<Data<BrowseDto>>().data
        return MangasPage(data.items.map { it.toSManga(baseUrl) }, data.pagination.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return getMangaUpdate(SManga.create().apply { this.url = slug }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.slug
        val knownId = manga.memo["mangaId"]?.intOrNull
        val details = if (fetchDetails || knownId == null) async { getDetails(slug).mangaDetails } else null
        val id = knownId ?: details!!.await().id
        val chapterList = if (fetchChapters) async { fetchChapterList(id, slug) } else null

        details?.await()?.applyTo(manga, baseUrl)
        SMangaUpdate(manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchChapterList(id: Int, slug: String): List<SChapter> {
        val url = apiUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id.toString())
            .addPathSegment("chapters")
            .build()
        return client.get(url).parseAs<Data<ChapterListDto>>().data.items.map { it.toSChapter(slug) }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = getDetails(manga.slug).recommendations.map { it.toSManga(baseUrl) }

    private suspend fun getDetails(slug: String): DetailsDto {
        val url = apiUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(slug)
            .build()
        return client.get(url).parseAs<Data<DetailsDto>>().data
    }

    // Madara-era entries: a post id with memo["path"], or a "/manga/<slug>/" url
    private val SManga.slug: String
        get() {
            val path = memo["path"]?.stringOrNull ?: url.takeIf { it.startsWith("/") } ?: return url
            return baseUrl.toHttpUrl().resolve(path)!!.pathSegments[1]
        }

    // Madara-era chapters: a bare slug with memo["mangaPath"], or a "/manga/<slug>/<chapter>/" url
    private val SChapter.path: String
        get() {
            val path = memo["mangaPath"]?.stringOrNull?.plus(url) ?: url.takeIf { it.startsWith("/") } ?: return url
            return baseUrl.toHttpUrl().resolve(path)!!.pathSegments.drop(1).filter(String::isNotEmpty).joinToString("/")
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegments(chapter.path)
            .build()
        val data = client.get(url).parseAs<Data<ChapterPagesDto>>().data
        return data.images.mapIndexed { index, image ->
            val imageUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("storage/mangas")
                .addPathSegment(data.mangaDir)
                .addPathSegment(data.chapterDir)
                .addPathSegment(image.name)
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder().addPathSegments("browse/genres").build()
        return client.get(url).parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Data<List<FilterOptionDto>>>()?.data.orEmpty()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                TypeFilter(),
                StatusFilter(),
                genres.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
                GenreModeFilter().takeIf { genres.isNotEmpty() },
            ),
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga")
        .addPathSegment(manga.slug)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga")
        .addPathSegments(chapter.path)
        .build()
        .toString()
}
