package eu.kanade.tachiyomi.extension.es.platinumlilyscan

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

@Source
abstract class PlatinumLilyScan : KeiSource() {

    private suspend fun fetchSeries(): List<SeriesDto> = client.get("$baseUrl/api/series").parseAs()

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val sortedSeries = fetchSeries().sortedByDescending { it.bookmarkCount }

        return MangasPage(sortedSeries.map { it.toSManga(baseUrl) }, false)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val sortedSeries = fetchSeries().sortedByDescending { it.updatedAtMillis }

        return MangasPage(sortedSeries.map { it.toSManga(baseUrl) }, false)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val series = fetchSeries()

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

        return MangasPage(filteredSeries.map { it.toSManga(baseUrl) }, false)
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        StatusFilter(),
        ContentRatingFilter(),
        GenreFilter(),
    )

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // manga.url is just the slug, so we rebuild the API path here
        val series = client.get("$baseUrl/api/series/${manga.url}").parseAs<SeriesDto>()

        // API already returns chapters in order; let the app sort if needed
        val chapterList = series.chapters?.filter { it.id.isNotEmpty() }?.map {
            it.toSChapter(series.slug)
        } ?: emptyList()

        return SMangaUpdate(series.toSManga(baseUrl), chapterList)
    }

    // ============================== Chapters ==============================
    override fun getChapterUrl(chapter: SChapter): String {
        // chapter.url is "seriesSlug#chapterId"
        val slug = chapter.url.substringBefore("#")
        return "$baseUrl/series/$slug"
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // chapter.url is "seriesSlug#chapterId"
        val seriesSlug = chapter.url.substringBefore("#")
        val chapterId = chapter.url.substringAfter("#")

        val series = client.get("$baseUrl/api/series/$seriesSlug").parseAs<SeriesDto>()
        val seriesChapter = series.chapters?.find { it.id == chapterId }
            ?: throw Exception("Capítulo no encontrado")

        return seriesChapter.pages?.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.imageUrl)
        } ?: emptyList()
    }
}
