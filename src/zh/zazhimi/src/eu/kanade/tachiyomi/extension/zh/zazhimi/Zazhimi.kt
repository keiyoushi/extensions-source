package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.lang.IllegalStateException

@Source
abstract class Zazhimi : KeiSource() {

    private val apiUrl = "https://android2026.zazhimi.net/api"

    override val supportsLatest = false

    override fun Headers.Builder.configureHeaders() = set("User-Agent", "ZaZhiMi_6.0.0")

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/index.php?p=$page&s=20").parseAs<IndexResponse>()
        val mangas = result.new.map(NewItem::toSManga)
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // Search

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("筛选条件（搜索关键字时无效）"),
        TypeFilter(),
        BrandFilter(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
        if (query.isEmpty()) {
            url.addPathSegment("lists.php")
                .addQueryParameter("c", filters[1].toString())
                .addQueryParameter("m", filters[2].toString())
        } else {
            url.addPathSegment("search.php").addQueryParameter("k", query)
        }
        url.addQueryParameter("p", page.toString()).addQueryParameter("s", "20")
        val result = client.get(url.build()).parseAs<SearchResponse>()
        return MangasPage(result.magazine.map(SearchItem::toSManga), true)
    }

    // Manga Detail Page / Chapters Page

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = client.get(apiUrl + manga.url).parseAs<ShowResponse>()
        if (result.content.isEmpty()) throw IllegalStateException("内容解析为空！")
        val item = result.content[0]
        val details = SManga.create().apply {
            title = item.magName
            author = item.magName.split(" ")[0]
            thumbnail_url = item.magPic
            url = "/show.php?a=${item.magId}"
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = "/show.php?a=${item.magId}"
            // Mihon strips the manga title from chapter names, which left this one blank
            name = "全本"
            chapter_number = 1F
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    // Manga View Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get(apiUrl + chapter.url).parseAs<ShowResponse>()
        return result.content.mapIndexed { i, it -> it.toPage(i) }
    }
}
