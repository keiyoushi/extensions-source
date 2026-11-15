package eu.kanade.tachiyomi.extension.en.yurivan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Yurivan : KeiSource() {

    override val supportsFilterFetching = true

    private val rscHeaders get() = headers.newBuilder().set("rsc", "1").build()

    // Story pages and the reader API sit behind an age gate that only checks this cookie
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addCookie("age_verified" to "true")

    override suspend fun getPopularManga(page: Int): MangasPage = storyFeed(page, sort = "top")

    override suspend fun getLatestUpdates(page: Int): MangasPage = storyFeed(page, sort = "fresh")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = filters.firstInstanceOrNull<TagFilter>()
        return storyFeed(
            page,
            sort = filters.firstInstanceOrNull<SortFilter>()?.value,
            query = query,
            tags = tags?.included.orEmpty(),
            excludedTags = tags?.excluded.orEmpty(),
        )
    }

    private suspend fun storyFeed(
        page: Int,
        sort: String?,
        query: String = "",
        tags: List<String> = emptyList(),
        excludedTags: List<String> = emptyList(),
    ): MangasPage {
        val url = "$baseUrl/api/story-feed".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "gallery")
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            sort?.let { addQueryParameter("sort", it) }
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            if (tags.isNotEmpty()) addQueryParameter("tag", tags.joinToString(","))
            if (excludedTags.isNotEmpty()) addQueryParameter("exclude_tag", excludedTags.joinToString(","))
        }.build()
        val data = client.get(url, Fetch.XHR).parseAs<FeedResponse>().data
        return MangasPage(data.items.map { it.toSManga() }, data.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "story") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val rsc = client.get(getMangaUrl(manga), rscHeaders, Fetch.XHR).use { it.body.string() }
        val series = rsc.extractNextJsRsc<LdJsonScript> { element ->
            element is JsonObject &&
                (element["type"] as? JsonPrimitive)?.content == "application/ld+json" &&
                (element["children"] as? JsonPrimitive)?.content?.contains("\"ComicSeries\"") == true
        }!!.children.parseAs<ComicSeriesDto>()
        val chapterList = rsc.extractNextJsRsc<ChapterListDto>()!!
        return SMangaUpdate(series.toSManga(manga.url), chapterList.toSChapters())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/story/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val storyId = chapter.memo[MEMO_STORY_ID]!!.string
        val chapterIndex = chapter.memo[MEMO_CHAPTER_INDEX]!!.int
        return "$baseUrl/story/$storyId/read?chapter=${chapterIndex + 1}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/api/reader-chapter".toHttpUrl().newBuilder()
            .addQueryParameter("story_id", chapter.memo[MEMO_STORY_ID]!!.string)
            .addQueryParameter("chapter_index", chapter.memo[MEMO_CHAPTER_INDEX]!!.int.toString())
            .build()
        return client.get(url, Fetch.XHR.from(getChapterUrl(chapter))).parseAs<ReaderChapterResponse>().toPages()
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tags", rscHeaders, Fetch.XHR)
        .extractNextJs<TagsDto>()!!
        .labels
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<Map<String, TagLabelDto>>()?.map { (slug, tag) -> Tag(tag.label, slug) }
        return FilterList(listOfNotNull(SortFilter(), tags?.let(::TagFilter)))
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
