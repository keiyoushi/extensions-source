package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Dilar : KeiSource() {

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/rankings")
        val data = response.parseAs<RankingsDto>()
        val entries = data.topSeries
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/series?page=$page")
        val data = response.parseAs<SeriesListDto>()
        val entries = data.series
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, data.hasNextPage)
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstanceOrNull<TypeFilter>()
        val categories = filters.filterIsInstance<CategoryFilter>()
        val body = SearchRequestDto(
            query = query,
            page = page,
            seriesType = IncludeExcludeDto(type?.included.orEmpty(), type?.excluded.orEmpty() + NOVEL_TYPE),
            categories = IncludeExcludeDto(
                categories.flatMap { it.included }.map(String::toInt),
                categories.flatMap { it.excluded }.map(String::toInt),
            ),
            oneshot = filters.firstInstanceOrNull<OneshotFilter>()?.state ?: false,
            storyStatus = IncludeExcludeDto(filters.firstInstanceOrNull<StoryStatusFilter>()?.checked.orEmpty(), emptyList()),
            translationStatus = IncludeExcludeDto(filters.firstInstanceOrNull<TranslationStatusFilter>()?.checked.orEmpty(), emptyList()),
            over17 = filters.firstInstanceOrNull<Over17Filter>()?.toValue(),
        ).toJsonRequestBody()
        val response = client.post("$baseUrl/api/search/filter", body)
        val data = response.parseAs<SearchListDto>()

        return MangasPage(data.rows.map { it.toSManga() }, data.hasNextPage)
    }

    // Filters

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/api/categories")
        .parseAs<List<CategoryGroupDto>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            TypeFilter(),
            StoryStatusFilter(),
            TranslationStatusFilter(),
            OneshotFilter(),
            Over17Filter(),
        ) + data?.parseAs<List<CategoryGroupDto>>().orEmpty().map { group ->
            CategoryFilter(group.name, group.categories.map { it.name to it.id })
        },
    )

    // Details & Chapters

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (!fetchDetails) return@async manga
            client.get("$baseUrl/api/series/${manga.getMangaId()}")
                .parseAs<SeriesDto>()
                .toSManga()
        }

        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            val response = client.get("$baseUrl/api/series/${manga.getMangaId()}/chapters")
            response.parseAs<ChapterListDto>().chapters.flatMap { chapter ->
                chapter.releases.map { it.toSChapter(chapter, manga.url) }
            }.sortedByDescending { it.chapter_number }
        }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    // Pages

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("reader")
        .addPathSegments(chapter.url.substringBeforeLast("#"))
        .addQueryParameter("release", chapter.url.substringAfterLast("#"))
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val bridge = List(12) { ('a'..'z').random() }.joinToString("")
        val releaseId = chapter.url.substringAfterLast("#")

        val data = runWebView<String> {
            domStorageEnabled = true
            blockImages = true
            userAgent = headers["User-Agent"]!!
            webMessageBridge(bridge, setOf(baseUrl)) { resolve(it) }
            injectAtDocumentStart(captureScript(bridge, releaseId), setOf(baseUrl))
            loadUrl(getChapterUrl(chapter))
        }.parseAs<PageListDto>()

        return data.pages.sortedBy { it.order }
            .mapIndexed { index, page ->
                val imageUrl = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("uploads/releases/${data.storageKey}/hq/${page.url}")
                    .addQueryParameter("t", data.mediaToken)
                    .build()
                    .toString()
                Page(index, imageUrl = imageUrl)
            }
    }

    // The chapter response is encrypted with a scheme the server keeps rotating (and it ignores
    // X-Crypto-Caps), so let the site's reader decrypt it and grab the result as it gets parsed.
    private fun captureScript(bridge: String, releaseId: String) = """
        (() => {
            const parse = JSON.parse;
            JSON.parse = function () {
                const result = parse.apply(this, arguments);
                if (result && String(result.id) === ${releaseId.toJsonString()} && result.storage_key && Array.isArray(result.pages)) {
                    window.$bridge.postMessage(JSON.stringify({
                        storage_key: result.storage_key,
                        media_token: result.media_token,
                        pages: result.pages,
                    }));
                }
                return result;
            };
        })();
    """.trimIndent()

    // common

    private fun SManga.getMangaId(): String = this.url.substringBeforeLast("/")

    fun createThumbnail(mangaId: String, cover: String): String {
        val thumbnail = "large_${cover.substringBeforeLast(".")}.webp"

        return "$baseUrl/uploads/manga/cover/$mangaId/$thumbnail"
    }

    companion object {
        private const val NOVEL_TYPE = "99"
    }
}
