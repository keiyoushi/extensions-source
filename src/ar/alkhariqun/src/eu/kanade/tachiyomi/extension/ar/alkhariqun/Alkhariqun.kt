package eu.kanade.tachiyomi.extension.ar.alkhariqun

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Alkhariqun : KeiSource() {

    override val supportsLatest = false

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { "storage" !in it.pathSegments }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/comics".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("title", query)
            filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("author", it) }
            filters.firstInstanceOrNull<CategoryFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("categories[]", it.id) }
            addQueryParameter("page", page.toString())
        }.build()
        val comics = client.get(url, Fetch.DOCUMENT).inertiaProps<ComicsProps>().comics
        return MangasPage(comics.data.map { it.toSManga() }, comics.meta.currentPage < comics.meta.lastPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.first() !in setOf("comics", "share")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/comics/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val props = client.get(getMangaUrl(manga), Fetch.DOCUMENT).inertiaProps<ComicProps>()
        return SMangaUpdate(props.comic.toSManga(), props.comic.chapters.map { it.toSChapter(props.comic.slug, props.locale) })
    }

    // "You may also like" carries only slugs and covers; titles come from the comics list
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = coroutineScope {
        val related = async { client.get(getMangaUrl(manga), Fetch.DOCUMENT).inertiaProps<ComicProps>().youMayAlsoLike }
        val firstPage = getComicsPage(1)
        val otherPages = (2..firstPage.meta.lastPage).map { async { getComicsPage(it) } }
        val comics = (listOf(firstPage) + otherPages.awaitAll()).flatMap { it.data }.associateBy { it.slug }
        related.await().mapNotNull { comics[it.slug]?.toSManga() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val props = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).inertiaProps<ChapterProps>()
        return props.chapterImages.mapIndexed { index, path -> Page(index, imageUrl = "$baseUrl/storage/$path") }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val props = client.get("$baseUrl/comics", Fetch.DOCUMENT).inertiaProps<ComicsProps>()
        return props.categories.map { it.toCategory(props.locale) }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(AuthorFilter())
            data?.parseAs<List<Category>>()?.takeIf { it.isNotEmpty() }?.let { add(CategoryFilter(it)) }
        },
    )

    private suspend fun getComicsPage(page: Int): Paginated {
        val url = "$baseUrl/comics".toHttpUrl().newBuilder().addQueryParameter("page", page.toString()).build()
        return client.get(url, Fetch.DOCUMENT).inertiaProps<ComicsProps>().comics
    }

    private inline fun <reified T> Response.inertiaProps(): T = asJsoup().selectFirst("#app")!!.attr("data-page").parseAs<InertiaPage<T>>().props
}
