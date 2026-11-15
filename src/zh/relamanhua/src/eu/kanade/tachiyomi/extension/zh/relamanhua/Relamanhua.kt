package eu.kanade.tachiyomi.extension.zh.relamanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Relamanhua : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getComics(page, "-popular", theme = null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getComics(page, "-datetime_updated", theme = null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val ordering = filters.firstInstanceOrNull<SortFilter>()?.ordering ?: "-datetime_updated"
            return getComics(page, ordering, filters.firstInstanceOrNull<ThemeFilter>()?.selected)
        }
        return api<ComicListDto>(
            "search/comic",
            "platform" to "1",
            "q" to query.trim(),
            "q_type" to "",
            "limit" to PAGE_SIZE.toString(),
            "offset" to ((page - 1) * PAGE_SIZE).toString(),
        ).toMangasPage()
    }

    // Free comics only: without a paid account, paid ones only open their first chapter
    private suspend fun getComics(page: Int, ordering: String, theme: String?): MangasPage = api<ComicListDto>(
        "comics",
        "free_type" to "1",
        "ordering" to ordering,
        "theme" to theme,
        "limit" to PAGE_SIZE.toString(),
        "offset" to ((page - 1) * PAGE_SIZE).toString(),
    ).toMangasPage()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.").removePrefix("m.") !in DOMAINS) return null
        val segments = url.pathSegments
        val pathWord = when {
            // Desktop pages: /comic/<path word>, and /comic/<path word>/chapter/<uuid> for a chapter
            segments[0] == "comic" -> segments.getOrNull(1)
            segments[0] != "v2h5" -> null
            segments.getOrNull(1) == "details" -> segments.getOrNull(3)
            segments.getOrNull(1) == "comicContent" -> segments.getOrNull(2)
            else -> null
        }?.takeIf { it.isNotEmpty() } ?: return null
        return getComic(pathWord).comic.toSMangaDetails().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/v2h5/details/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The chapter lists are per group, and only the details give the groups
        val comic = getComic(manga.url)
        val chapterList = if (fetchChapters) comic.groups.values.flatMap { getChapters(manga.url, it) } else chapters
        return SMangaUpdate(comic.comic.toSMangaDetails(), chapterList)
    }

    private suspend fun getComic(pathWord: String): ComicDetailsDto = api("comic2/$pathWord", "platform" to "1")

    private suspend fun getChapters(pathWord: String, group: GroupDto): List<SChapter> {
        val chapters = mutableListOf<ChapterDto>()
        do {
            val page = api<ChapterListDto>(
                "comic/$pathWord/group/${group.pathWord}/chapters",
                "limit" to CHAPTER_PAGE_SIZE.toString(),
                "offset" to chapters.size.toString(),
            )
            chapters += page.list
        } while (chapters.size < page.total && page.list.isNotEmpty())
        return chapters.asReversed().map { it.toSChapter(group) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/v2h5/comicContent/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (pathWord, uuid) = chapter.url.split('/')
        val result = api<ChapterResultDto>("comic/$pathWord/chapter/$uuid", "platform" to "1")
        // A locked chapter only gives the first few pages as a preview
        if (result.isLock) return emptyList()
        return result.chapter.contents.mapIndexed { index, image -> Page(index, imageUrl = image.url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("輸入關鍵字搜尋時，篩選不會生效"),
        SortFilter(),
        ThemeFilter(),
    )

    private suspend inline fun <reified T> api(path: String, vararg query: Pair<String, String?>): T {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply { query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) } }
            .build()
        val response = client.get(url, Fetch.XHR).parseAs<ResponseDto>()
        if (response.code != 200) throw Exception(response.message)
        return response.results.parseAs()
    }

    companion object {
        private const val API_URL = "https://api.manga2026.xyz/api/v3"

        // The same site on both domains, with m. (mobile), www. or no prefix
        private val DOMAINS = listOf("2024manga.com", "manga2026.xyz")
        private const val PAGE_SIZE = 30
        private const val CHAPTER_PAGE_SIZE = 500
    }
}
