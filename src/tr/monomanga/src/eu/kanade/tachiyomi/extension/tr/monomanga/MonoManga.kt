package eu.kanade.tachiyomi.extension.tr.monomanga

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

@Source
abstract class MonoManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val rscHeaders: Headers
        get() = headers.newBuilder().add("RSC", "1").build()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/manga?page=$page&sort=most_chapters".toHttpUrl())

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("article.manga-card")
            .filterNot { element ->
                element.select("span").any { it.text().lowercase(Locale.ROOT) == "novel" }
            }
            .map { element ->
                val a = element.selectFirst("a")!!
                SManga.create().apply {
                    setUrlWithoutDomain(a.absUrl("href"))
                    title = element.selectFirst("h3")?.text() ?: a.attr("title")
                    thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                }
            }
        val hasNextPage = document.select("nav[aria-label=Sayfalama] a[aria-label=Sonraki sayfa]:not([disabled])").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/manga?page=$page&sort=newest".toHttpUrl())

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        filters.firstInstanceOrNull<GenreFilter>()
            ?.selectedValue()
            ?.takeIf { it != "all" }
            ?.let { url.addQueryParameter("genre", it) }

        filters.firstInstanceOrNull<StatusFilter>()
            ?.selectedValue()
            ?.takeIf { it != "all" }
            ?.let { url.addQueryParameter("status", it) }

        filters.firstInstanceOrNull<TypeFilter>()
            ?.selectedValue()
            ?.takeIf { it != "all" }
            ?.let { url.addQueryParameter("type", it) }

        val sort = filters.firstInstanceOrNull<SortFilter>()?.selectedValue() ?: "newest"
        url.addQueryParameter("sort", sort)

        return parseMangaList(url.build())
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get(baseUrl + manga.url, rscHeaders).extractNextJs<MangaPageDto> {
            it is JsonObject && "manga" in it && "initialChapters" in it
        } ?: throw Exception("Manga detayları ayıklanamadı (Failed to extract manga details)")

        val updatedChapters = if (fetchChapters) chapterListParse(dto) else chapters

        return SMangaUpdate(mangaDetailsParse(dto), updatedChapters)
    }

    private fun mangaDetailsParse(dto: MangaPageDto): SManga = SManga.create().apply {
        title = dto.manga.name
        author = dto.manga.author
        artist = dto.manga.artist
        description = dto.manga.summary

        val tags = dto.manga.genres?.map { it.name }?.toMutableList() ?: mutableListOf()
        dto.manga.type?.let {
            tags.add(
                it.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                },
            )
        }
        genre = tags.joinToString()

        status = when (dto.manga.status?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = dto.manga.coverImage?.let {
            if (it.startsWith("http")) it else "https://cdn.monomanga.com.tr/$it"
        }
    }

    // ============================= Chapters ==============================

    private suspend fun chapterListParse(dto: MangaPageDto): List<SChapter> {
        val chapters = dto.initialChapters.map { it.toSChapter(dto.manga.slug) }.toMutableList()

        if (dto.initialHasMore) {
            val fetchedIds = chapters.map { it.url }.toMutableSet()

            if (!dto.manga.volumes.isNullOrEmpty()) {
                // The site groups remaining chapters by volumes.
                // We reverse the array to ensure descending order is maintained when appending chunks.
                for (volume in dto.manga.volumes.reversed()) {
                    val minCh = volume.startChapter.toString().removeSuffix(".0")
                    val maxCh = volume.endChapter.toString().removeSuffix(".0")
                    val apiDto = client.get("$baseUrl/api/manga/${dto.manga.id}/chapters?sort=desc&minChapter=$minCh&maxChapter=$maxCh")
                        .parseAs<ChapterListResponseDto>()
                    for (ch in apiDto.data) {
                        val sChapter = ch.toSChapter(dto.manga.slug)
                        if (fetchedIds.add(sChapter.url)) {
                            chapters.add(sChapter)
                        }
                    }
                }
            } else {
                // Fallback offset pagination if volumes are missing.
                var offset = chapters.size
                var hasMore = true
                while (hasMore) {
                    val apiDto = client.get("$baseUrl/api/manga/${dto.manga.id}/chapters?sort=desc&limit=100&offset=$offset")
                        .parseAs<ChapterListResponseDto>()
                    for (ch in apiDto.data) {
                        val sChapter = ch.toSChapter(dto.manga.slug)
                        if (fetchedIds.add(sChapter.url)) {
                            chapters.add(sChapter)
                        }
                    }
                    hasMore = apiDto.hasMore
                    offset = apiDto.nextOffset ?: (offset + apiDto.data.size)
                }
            }
        }
        return chapters
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = client.get(baseUrl + chapter.url, rscHeaders).extractNextJs<ChapterPageDto> {
            it is JsonObject && (it["chapter"] as? JsonObject)?.containsKey("content") == true
        } ?: throw Exception("Sayfa listesi ayıklanamadı (Failed to extract page list)")

        val content = dto.chapter.content ?: emptyList()
        return content.mapIndexed { i, img ->
            val url = if (img.startsWith("http")) img else "https://cdn.monomanga.com.tr/$img"
            Page(i, imageUrl = url)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )
}
