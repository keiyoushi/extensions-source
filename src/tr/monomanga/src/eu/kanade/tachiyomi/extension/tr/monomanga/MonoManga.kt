package eu.kanade.tachiyomi.extension.tr.monomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

class MonoManga : HttpSource() {

    override val name = "Mono Manga"

    override val baseUrl = "https://monomanga.com.tr"

    override val lang = "tr"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga?page=$page&sort=most_chapters", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
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

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga?page=$page&sort=newest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers.newBuilder().add("RSC", "1").build())

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.extractNextJs<MangaPageDto> {
            it is JsonObject && "manga" in it && "initialChapters" in it
        } ?: throw Exception("Manga detayları ayıklanamadı (Failed to extract manga details)")

        return SManga.create().apply {
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
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.extractNextJs<MangaPageDto> {
            it is JsonObject && "manga" in it && "initialChapters" in it
        } ?: throw Exception("Bölüm listesi ayıklanamadı (Failed to extract chapter list)")

        val chapters = dto.initialChapters.map { it.toSChapter(dto.manga.slug) }.toMutableList()

        if (dto.initialHasMore) {
            val fetchedIds = chapters.map { it.url }.toMutableSet()

            if (!dto.manga.volumes.isNullOrEmpty()) {
                // The site groups remaining chapters by volumes.
                // We reverse the array to ensure descending order is maintained when appending chunks.
                for (volume in dto.manga.volumes.reversed()) {
                    val minCh = volume.startChapter.toString().removeSuffix(".0")
                    val maxCh = volume.endChapter.toString().removeSuffix(".0")
                    val req = GET("$baseUrl/api/manga/${dto.manga.id}/chapters?sort=desc&minChapter=$minCh&maxChapter=$maxCh", headers)
                    val res = client.newCall(req).execute()
                    val apiDto = res.parseAs<ChapterListResponseDto>()
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
                    val req = GET("$baseUrl/api/manga/${dto.manga.id}/chapters?sort=desc&limit=100&offset=$offset", headers)
                    val res = client.newCall(req).execute()
                    val apiDto = res.parseAs<ChapterListResponseDto>()
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

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers.newBuilder().add("RSC", "1").build())

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.extractNextJs<ChapterPageDto> {
            it is JsonObject && (it["chapter"] as? JsonObject)?.containsKey("content") == true
        } ?: throw Exception("Sayfa listesi ayıklanamadı (Failed to extract page list)")

        val content = dto.chapter.content ?: emptyList()
        return content.mapIndexed { i, img ->
            val url = if (img.startsWith("http")) img else "https://cdn.monomanga.com.tr/$img"
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )
}
