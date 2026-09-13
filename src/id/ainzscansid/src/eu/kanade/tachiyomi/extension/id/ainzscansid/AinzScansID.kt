package eu.kanade.tachiyomi.extension.id.ainzscansid

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class AinzScansID : KeiSource() {

    private val apiUrl = "https://api.ainzscans01.com/api"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("sort", "views")
            .addQueryParameter("order", "desc")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).parseAs<SearchResponseDto>().toMangasPage(page)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("sort", "latest")
            .addQueryParameter("order", "desc")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).parseAs<SearchResponseDto>().toMangasPage(page)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())

        url.addQueryParameter("q", query)

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.selectedValue())
                is OrderFilter -> url.addQueryParameter("order", filter.selectedValue())
                is StatusFilter -> url.addQueryParameter("status", filter.selectedValue())
                is GenreFilter -> url.addQueryParameter("genre", filter.selectedValue())
                is TypeFilter -> url.addQueryParameter("comic_type", filter.selectedValue())
                is ColorFilter -> url.addQueryParameter("color_format", filter.selectedValue())
                is ReadingFilter -> url.addQueryParameter("reading_format", filter.selectedValue())
                is TextFilter -> url.addQueryParameter(filter.queryKey, filter.state)
                else -> {}
            }
        }

        return client.get(url.build()).parseAs<SearchResponseDto>().toMangasPage(page)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${getNormalizedMangaUrl(manga)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ======================= Details and Chapters ==========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/series${getNormalizedMangaUrl(manga)}").parseAs<SeriesDetailDto>()
        val comicSlug = dto.toSManga().url.substringAfterLast("/")
        return SMangaUpdate(dto.toSManga(), dto.units.map { it.toSChapter(comicSlug) })
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/series${chapter.url}").parseAs<ChapterDetailDto>().toPageList()

    // =============================== Filters ===============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        OrderFilter(),
        StatusFilter(),
        GenreFilter(),
        TypeFilter(),
        ColorFilter(),
        ReadingFilter(),
        TextFilter("Author", "author"),
        TextFilter("Artist", "artist"),
        TextFilter("Publisher", "publisher"),
    )

    private fun getNormalizedMangaUrl(manga: SManga): String = if (manga.url.startsWith("/series/")) {
        "/comic/${manga.url.substringAfter("/series/").removeSuffix("/")}"
    } else {
        manga.url
    }
}
