package eu.kanade.tachiyomi.extension.vi.suicaodex

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class SuicaoDex : KeiSource() {

    private val apiUrl get() = "https://redive.${baseUrl.toHttpUrl().host}"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular =======================================

    // "BXH truyện" ranking on the homepage. Server-side rendered, not paginated.
    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val document = client.get("$baseUrl/").asJsoup()
        val section = document.selectFirst("section[aria-labelledby=home-manga-ranking-heading]")
            ?: return MangasPage(emptyList(), false)

        val mangas = section.select("ol > li > a[href^=/manga/]").mapNotNull { element ->
            val href = element.attrOrNull("href") ?: return@mapNotNull null
            val id = href.removePrefix("/manga/").substringBefore('/')
            SManga.create().apply {
                url = id
                title = element.selectFirst("img")?.attrOrNull("alt")
                    ?.removePrefix("Bìa truyện ")
                    ?: element.selectFirst("span.line-clamp-2")?.text()
                    ?: throw IllegalArgumentException("Missing manga title in ranking entry")
                thumbnail_url = element.selectFirst("img")?.attrOrNull("src")
                memo = buildJsonObject {
                    put("slug", href.substringAfterLast('/'))
                }
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Latest ========================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchManga(page, "", FilterList())

    // ============================== Search ========================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchManga(page, query, filters)

    private suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/v1/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "36")

            val sort = filters.firstInstanceOrNull<SortFilter>()
            addQueryParameter("sort", sort?.toApiValue() ?: "latestChapter")
            addQueryParameter("direction", sort?.direction() ?: "desc")

            addQueryParameter(
                "hasChapter",
                filters.firstInstanceOrNull<HasChapterFilter>()?.toApiValue() ?: "true",
            )

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.firstInstanceOrNull<StatusFilter>()?.toApiValue()?.let {
                addQueryParameter("status", it)
            }
            filters.firstInstanceOrNull<YearFilter>()?.toApiValue()?.let {
                addQueryParameter("year", it)
            }
            filters.firstInstanceOrNull<AuthorFilter>()?.ids()?.forEach {
                addQueryParameter("author", it)
            }
            filters.firstInstanceOrNull<ArtistFilter>()?.ids()?.forEach {
                addQueryParameter("artist", it)
            }

            filters.filterIsInstance<TagGroupFilter>().flatMap { it.state }.forEach { tag ->
                when (tag.state) {
                    Filter.TriState.STATE_INCLUDE -> addQueryParameter("includeTag", tag.tagId)
                    Filter.TriState.STATE_EXCLUDE -> addQueryParameter("excludeTag", tag.tagId)
                }
            }
            addQueryParameter(
                "includeTagMode",
                filters.firstInstanceOrNull<IncludeTagModeFilter>()?.toApiValue() ?: "and",
            )
            addQueryParameter(
                "excludeTagMode",
                filters.firstInstanceOrNull<ExcludeTagModeFilter>()?.toApiValue() ?: "or",
            )
        }.build()

        val result = client.get(url).parseAs<ListResponse<MangaDto>>()
        val mangas = result.data.map { it.toListSManga() }
        return MangasPage(mangas, hasNextPage = result.meta.page < result.meta.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        return when (url.pathSegments.getOrNull(0)) {
            "manga" -> {
                val id = url.pathSegments.getOrNull(1) ?: return null
                client.get("$apiUrl/v1/manga/$id").parseAs<MangaDto>().toSManga()
            }
            "chapter" -> {
                val chapterId = url.pathSegments.getOrNull(1) ?: return null
                val chapter = client.get("$apiUrl/v1/chapters/$chapterId")
                    .parseAs<ChapterDetailDto>()
                val mangaId = chapter.manga?.id ?: return null
                client.get("$apiUrl/v1/manga/$mangaId").parseAs<MangaDto>().toSManga()
            }
            else -> null
        }
    }

    // ============================== Details & Chapters ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = if (fetchDetails) {
            async { client.get("$apiUrl/v1/manga/${manga.url}").parseAs<MangaDto>().toSManga() }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchAllChapters(manga.url) }
        } else {
            null
        }

        SMangaUpdate(
            manga = detailsDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    private suspend fun fetchAllChapters(mangaId: String): List<SChapter> = coroutineScope {
        fun chapterUrl(page: Int) = "$apiUrl/v1/manga/$mangaId/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("language", "vi")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "100")
            .build()

        val first = client.get(chapterUrl(1)).parseAs<ListResponse<ChapterDto>>()
        val chapters = first.data.map { it.toSChapter() }.toMutableList()

        if (first.meta.totalPages > 1) {
            val rest = (2..first.meta.totalPages).map { page ->
                async { client.get(chapterUrl(page)).parseAs<ListResponse<ChapterDto>>() }
            }.awaitAll()
            rest.forEach { result -> chapters += result.data.map { it.toSChapter() } }
        }

        chapters
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]?.stringOrNull
        return "$baseUrl/manga/${manga.url}" + (slug?.let { "/$it" } ?: "")
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    // ============================== Pages =========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get("$apiUrl/v1/chapters/${chapter.url}")
            .parseAs<ChapterDetailDto>()

        return data.pages.sortedBy { it.position }.mapIndexed { index, page ->
            val imageUrl = page.url ?: page.replicaUrl
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters =======================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/v1/tags").parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.get("data")?.parseAs<List<TagDto>>().orEmpty()

        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(HasChapterFilter())
                add(YearFilter())
                add(AuthorFilter())
                add(ArtistFilter())
                if (tags.isNotEmpty()) {
                    add(Filter.Separator())
                    add(Filter.Header("Thể loại"))
                    add(IncludeTagModeFilter("Bao gồm"))
                    add(ExcludeTagModeFilter("Loại trừ"))
                    tags.groupBy { it.group }.forEach { (group, groupTags) ->
                        add(TagGroupFilter(group, groupTags))
                    }
                }
            },
        )
    }

    private class IncludeTagModeFilter(name: String) : TagModeFilter(name)
    private class ExcludeTagModeFilter(name: String) : TagModeFilter(name)
}
