package eu.kanade.tachiyomi.extension.es.platinumlilyscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class PlatinumLilyScan : HttpSource() {

    override val name = "Platinum Lily Scan"
    override val baseUrl = "https://platinumlilyscan.com"
    override val lang = "es"
    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val series = response.parseAs<List<SeriesDto>>()
        val sortedSeries = series.sortedByDescending { it.bookmarkCount }

        return MangasPage(sortedSeries.map { it.toSManga(baseUrl) }, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/series", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val series = response.parseAs<List<SeriesDto>>()
        val sortedSeries = series.sortedByDescending { it.updatedAtMillis }

        return MangasPage(sortedSeries.map { it.toSManga(baseUrl) }, false)
    }

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(searchMangaRequest(page, query, filters))
        .asObservableSuccess()
        .map { response ->
            val series = response.parseAs<List<SeriesDto>>()

            val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
            val ratingFilter = filters.firstInstanceOrNull<ContentRatingFilter>()
            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

            val selectedType = typeFilter?.let { if (it.state == 0) null else it.values[it.state] }
            val mappedType = when (selectedType) {
                "Manga" -> "MANGA"
                "Manhwa" -> "MANHWA"
                "Manhua" -> "MANHUA"
                "Doujinshi" -> "DOUJINSHI"
                "One-Shot" -> "ONE_SHOT"
                else -> null
            }

            val selectedStatus = statusFilter?.let { if (it.state == 0) null else it.values[it.state] }
            val mappedStatus = when (selectedStatus) {
                "Publicándose" -> "ONGOING"
                "Finalizado" -> "COMPLETED"
                "Hiatus" -> "HIATUS"
                else -> null
            }

            val selectedRating = ratingFilter?.let { if (it.state == 0) null else it.values[it.state] }
            val mappedRating = when (selectedRating) {
                "Seguro" -> "SAFE"
                "Sugestivo" -> "SUGGESTIVE"
                "NSFW" -> "NSFW"
                else -> null
            }

            val selectedGenre = genreFilter?.let { if (it.state == 0) null else it.values[it.state] }

            val filteredSeries = series.filter { manga ->
                val matchQuery = query.isBlank() || manga.matchQuery(query)
                matchQuery && manga.matches(mappedType, mappedStatus, mappedRating, selectedGenre)
            }.sortedByDescending { it.updatedAtMillis }

            MangasPage(filteredSeries.map { it.toSManga(baseUrl) }, false)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/series", headers)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        ContentRatingFilter(),
        GenreFilter(),
    )

    // =========================== Manga Details ============================
    // manga.url is now just the slug, so we rebuild the API path here
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDto>().toSManga(baseUrl)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api/series/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.parseAs<SeriesDto>()

        // API already returns chapters in order; let the app sort if needed
        return series.chapters?.filter { it.id.isNotEmpty() }?.map {
            it.toSChapter(series.slug)
        } ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        // chapter.url is "seriesSlug#chapterId"
        val slug = chapter.url.substringBefore("#")
        return "$baseUrl/series/$slug"
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        // chapter.url is "seriesSlug#chapterId"
        val seriesSlug = chapter.url.substringBefore("#")
        val chapterId = chapter.url.substringAfter("#")
        return GET("$baseUrl/api/series/$seriesSlug", headers).newBuilder()
            .tag(String::class.java, chapterId)
            .build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.tag(String::class.java)
            ?: throw Exception("ID de capítulo no encontrado")

        val series = response.parseAs<SeriesDto>()
        val chapter = series.chapters?.find { it.id == chapterId }
            ?: throw Exception("Capítulo no encontrado")

        return chapter.pages?.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.imageUrl)
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
