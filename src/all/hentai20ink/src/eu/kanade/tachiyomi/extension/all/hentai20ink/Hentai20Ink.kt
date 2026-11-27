package eu.kanade.tachiyomi.extension.all.hentai20ink

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Hentai20Ink : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    // We can't filter in `parseArchive` as it breaks pagination
    override suspend fun getPopularManga(page: Int) = super.getPopularManga(page).filterByLanguage()
    override suspend fun getLatestUpdates(page: Int) = super.getLatestUpdates(page).filterByLanguage()
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = super.getSearchMangaList(page, query, filters).filterByLanguage()
    override suspend fun fetchRelatedMangaList(id: String, genres: List<GenreRoute>) = super.fetchRelatedMangaList(id, genres).filter { it.matchesLanguage() }

    private fun MangasPage.filterByLanguage() = MangasPage(mangas.filter { it.matchesLanguage() }, hasNextPage)
    private fun SManga.matchesLanguage() = when (lang) {
        "en" -> !title.endsWith(" Raw")
        "ko" -> title.endsWith(" Raw")
        else -> true
    }
}
