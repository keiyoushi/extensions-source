package eu.kanade.tachiyomi.extension.th.toonjai

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
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Toonjai : KeiSource() {

    private val rscHeaders get() = headersBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int) = getMangaList("popular", page)

    override suspend fun getLatestUpdates(page: Int) = getMangaList("latest", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.slug.orEmpty()
        return getMangaList(if (category.isEmpty()) "latest" else "categories/$category", page, query.trim())
    }

    private suspend fun getMangaList(path: String, page: Int, query: String = ""): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(path)
            if (query.isNotEmpty()) addQueryParameter("q", query)
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        val rsc = getRsc(url)
        // A search without results has no item list at all
        val mangas = rsc.extractNextJsRsc<ItemsDto>()?.items.orEmpty().map { it.toSManga() }
        val hasNextPage = rsc.extractNextJsRsc<NavLinkDto> { it is JsonObject && it["aria-label"]?.jsonPrimitive?.contentOrNull == NEXT_PAGE } != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val content = findContent(manga.url.substringAfterLast('/'))!!
        return SMangaUpdate(content.toSMangaDetails(), content.toSChapters())
    }

    // The page also carries the cards of related series; an unknown series is a not-found page in a 200 response
    private suspend fun findContent(slug: String) = getRsc("$baseUrl/content/$slug".toHttpUrl())
        .extractNextJsRsc<ContentDto> { it is JsonObject && it["slug"]?.jsonPrimitive?.contentOrNull == slug && "episodes" in it }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val path = url.sitePath()
        if (path?.size == 2 && path[0] == "categories") {
            return getMangaList("categories/${path[1]}", page, url.queryParameter("q").orEmpty())
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.sitePath() ?: return null
        if (path.size < 2 || path[0] != "content") return null
        return findContent(path[1])?.toSMangaDetails()?.apply { initialized = true }
    }

    private fun HttpUrl.sitePath() = pathSegments.filter(String::isNotEmpty)
        .takeIf { host.removePrefix("www.") == baseUrl.toHttpUrl().host }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.name.startsWith("🔒")) return emptyList()
        val reader = getRsc(getChapterUrl(chapter).toHttpUrl()).extractNextJsRsc<ReaderDto>()!!
        if (reader.isRestricted) return emptyList()
        return reader.images.mapIndexed { i, image -> Page(i, imageUrl = image) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // Server component payload of a page; without _rsc the site answers with a redirect first
    private suspend fun getRsc(url: HttpUrl): String {
        val rscUrl = url.newBuilder().addQueryParameter("_rsc", "").build()
        return client.get(rscUrl, rscHeaders, Fetch.XHR).use { it.body.string() }
    }

    companion object {
        private const val NEXT_PAGE = "หน้าถัดไป"
    }
}
