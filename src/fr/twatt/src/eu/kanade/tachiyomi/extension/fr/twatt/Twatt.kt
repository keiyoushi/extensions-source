package eu.kanade.tachiyomi.extension.fr.twatt

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.dataimage.DataImageInterceptor
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import kotlin.time.Instant

@Source
abstract class Twatt : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(DataImageInterceptor())

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val projects = client.get("$baseUrl/api/projects").parseAs<ProjectsResponse>().projects
        val mangas = projects.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangasPage = getPopularManga(page)
        val filtered = mangasPage.mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(filtered, false)
    }

    // ============================= Manga Update ===========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get(
            "$baseUrl/api/series/${manga.url.substringAfterLast('/')}",
        ).parseAs<SeriesResponse>()

        return SMangaUpdate(
            manga = mangaDetailsParse(data),
            chapters = chapterListParse(data),
        )
    }

    private fun mangaDetailsParse(data: SeriesResponse) = data.project.toSManga(baseUrl).apply {
        data.mainTeam?.let { author = it.name }
    }

    private fun chapterListParse(data: SeriesResponse) = data.chapters.map { entry ->
        SChapter.create().apply {
            url = "/chapitre/${entry.id}"
            name = entry.title?.ifBlank { null }
                ?: "Chapitre ${entry.number}"
            chapter_number = entry.number.toFloat()
            date_upload = Instant.tryParse(entry.releasedAt)
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/chapters/${chapter.url.substringAfterLast('/')}")
        val images = response.parseAs<ChapterResponse>().chapter.images
        return images.mapIndexed { i, path ->
            Page(i, imageUrl = resolvePath(path, baseUrl))
        }
    }
}
