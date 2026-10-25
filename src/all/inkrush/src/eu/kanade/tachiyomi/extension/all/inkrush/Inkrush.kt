package eu.kanade.tachiyomi.extension.all.inkrush

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Inkrush : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getCatalog(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCatalog(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getCatalog(
        page,
        filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart() ?: "trending",
        query,
        filters.firstInstanceOrNull<TypeFilter>()?.toUriPart().orEmpty(),
    )

    private suspend fun getCatalog(page: Int, category: String, query: String = "", type: String = ""): MangasPage {
        val url = apiUrl("catalog").apply {
            addQueryParameter("q", query)
            addQueryParameter("language", lang)
            addQueryParameter("category", category)
            addQueryParameter("page", page.toString())
            if (type.isNotEmpty()) addQueryParameter("type", type)
        }.build()
        val catalog = client.get(url).parseAs<CatalogDto>()
        return MangasPage(catalog.comics.map { it.toSManga(baseUrl) }, catalog.hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = getDetail(manga.url)
        return SMangaUpdate(
            detail.toSManga(baseUrl),
            detail.chapters.map { it.toSChapter(manga.url) }.reversed(),
        )
    }

    private suspend fun getDetail(id: String): DetailDto {
        val url = apiUrl("detail").apply {
            addQueryParameter("id", id)
            addQueryParameter("language", lang)
            addQueryParameter("compact", "1")
        }.build()
        return client.get(url).parseAs()
    }

    // each chapter lists releases from several upstream groups; without `release`, the site
    // serves its own default pick, which is what the web reader shows too.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl("pages")
            .addQueryParameter("id", chapter.url.substringAfter('/'))
            .build()
        return client.get(url).parseAs<PagesDto>().pages.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.src)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        CategoryFilter(),
        TypeFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/#title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/#read/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.takeIf { it.firstOrNull() == "comics" }?.getOrNull(1)
            ?: url.fragment?.split('/')?.takeIf { it.first() == "title" || it.first() == "read" }?.getOrNull(1)
            ?: return null
        return getDetail(id).toSManga(baseUrl)
    }

    private fun apiUrl(action: String) = "$baseUrl/api/library".toHttpUrl().newBuilder()
        .addQueryParameter("action", action)
}
