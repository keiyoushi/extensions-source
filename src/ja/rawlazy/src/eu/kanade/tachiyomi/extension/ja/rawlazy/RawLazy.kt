package eu.kanade.tachiyomi.extension.ja.rawlazy

import eu.kanade.tachiyomi.multisrc.zingtheme.GenreGroup
import eu.kanade.tachiyomi.multisrc.zingtheme.OriginCache
import eu.kanade.tachiyomi.multisrc.zingtheme.ZingTheme
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class RawLazy : ZingTheme() {

    override val mangaPathSegment = "manga-lazy"
    override val chapterIdParam = "p"

    /** Categories rawfree has but this site does not; verified against the live site. */
    override val unsupportedGenres = setOf("子育て", "競馬･競輪･競艇", "少年愛")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = callZing(ACTION_TOP)
        return MangasPage(result.mes.asFragment(result.origin).select(TOP_MANGA_SELECTOR).map(::mangaFromElement), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = listPage(ALL_CATEGORIES, page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s_manga", query)
                .build()
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            // The search results are rendered server-side in one shot; the theme has no paging
            // for them, and `?paged=2` just serves the same rows again.
            return MangasPage(document.select(mangaListSelector).map(::mangaFromElement), false)
        }

        val genres = filters.filterIsInstance<GenreGroup>().flatMap { it.selected }
        val origin = baseUrl
        return when {
            genres.isEmpty() -> listPage(ALL_CATEGORIES, page)
            genres.size > MAX_GENRES -> throw Exception("ジャンルは最大 $MAX_GENRES 個までです")
            else -> mergeGenrePages(genres, page) { genre, p -> listPage(categoryId(genre, origin), p, origin) }
        }
    }

    /**
     * The listing that the site's own browse page drives. Its HTML category archives are
     * unreliable - `/category/<slug>/` renders an empty first page - so this ajax action is the
     * only dependable way to filter.
     */
    private suspend fun listPage(categoryId: String, page: Int, origin: String = baseUrl): MangasPage {
        val result = callZing(ACTION_LIST, "p" to page.toString(), "category_id" to categoryId, origin = origin)
        val mangas = result.mes.asFragment(result.origin).select(mangaListSelector).map(::mangaFromElement)
        return MangasPage(mangas, result.hasMore && mangas.isNotEmpty())
    }

    /**
     * `loadmore_tr` takes a numeric term id, but a filter can only carry the name, so the two are
     * bridged through the REST API and cached only for the chosen deployment.
     */
    private val categoryIds = OriginCache<String> { baseUrl }

    private suspend fun categoryId(name: String, origin: String): String = categoryIds.get(origin, name) {
        val url = "$origin/wp-json/wp/v2/categories".toHttpUrl().newBuilder()
            .addQueryParameter("slug", name)
            .addQueryParameter("_fields", "id,name")
            .build()
        val id = client.get(url, Fetch.XHR.from("$origin/")).parseAs<List<Category>>()
            .firstOrNull { it.name == name }?.id?.toString()
            ?: throw Exception("ジャンル「$name」が見つかりません")
        id
    }

    @Serializable
    private class Category(val id: Int, val name: String)

    companion object {
        private const val ACTION_LIST = "loadmore_tr"
        private const val ACTION_TOP = "load_top"
        private const val TOP_MANGA_SELECTOR = "div.swiper-slide div.entry"

        /** What the site's own browse page sends when no category is picked. */
        private const val ALL_CATEGORIES = "0"
    }
}
