package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class BigSolo : KeiSource() {

    override val supportsLatest = true

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val series = client.get("$baseUrl/data/series").parseAs<SeriesResponse>()
        return MangasPage(series.reco.map { it.toDetailedSManga() }, false)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val allSeries = fetchAllSeries()
        return MangasPage(allSeries.map { it.toDetailedSManga() }, false)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val allSeries = fetchAllSeries()

        val filtered = allSeries.filter { serie ->
            query.isBlank() ||
                serie.title.contains(query, ignoreCase = true) ||
                serie.alternativeTitles.any { it.contains(query, ignoreCase = true) } ||
                serie.jaTitle.contains(query, ignoreCase = true)
        }

        return MangasPage(filtered.map { it.toDetailedSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return client.get("$baseUrl/data/series/$slug").parseAs<Serie>().toDetailedSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga${chapter.url}"

    private suspend fun fetchAllSeries(): List<Serie> {
        val series = client.get("$baseUrl/data/series").parseAs<SeriesResponse>()
        return (series.series + series.os).sortedByDescending { it.lastChapter?.timestamp }
    }

    // Details & Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removePrefix("/")
        val serie = client.get("$baseUrl/data/series/$slug").parseAs<Serie>()
        return SMangaUpdate(serie.toDetailedSManga(), buildChapterList(serie))
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterId) = chapter.url.removePrefix("/").split("/")
        val chapterDetails = client.get("$baseUrl/data/series/$slug/$chapterId").parseAs<ChapterDetails>()
        return chapterDetails.images.mapIndexed { index, pageData ->
            Page(index, imageUrl = pageData)
        }
    }

    private fun buildChapterList(serie: Serie): List<SChapter> {
        val chapters = serie.chapters
        val chapterList = mutableListOf<SChapter>()
        val multipleChapters = chapters.size > 1

        for ((chapterNumber, chapterData) in chapters) {
            if (chapterData.licencied || chapterData.source == null) continue

            val title = chapterData.title
            val volumeNumber = chapterData.volume

            val baseName = if (multipleChapters) {
                buildString {
                    if (!volumeNumber.isNullOrBlank()) append("Vol. $volumeNumber ")
                    append("Ch. $chapterNumber")
                    if (title.isNotBlank()) append(" – $title")
                }
            } else {
                title.ifBlank { "One Shot" }
            }

            val chapter = SChapter.create().apply {
                name = baseName
                setUrlWithoutDomain("$baseUrl/${serie.slug}/$chapterNumber")
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                scanlator = chapterData.teams.joinToString(" & ")
                date_upload = chapterData.timestamp * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }
}
