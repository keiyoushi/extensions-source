package eu.kanade.tachiyomi.extension.es.eflee

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.GenreList
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.nodes.Document

class Eflee : ZeistManga(
    "Edens Fairy",
    "https://www.eflee.co",
    "es",
) {
    override val popularMangaSelector = "#PopularPosts3 article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override val useNewChapterFeed = true
    override val chapterCategory = "Cap"

    override val hasFilters = true
    override val hasLanguageFilter = false
    override val hasGenreFilter = false

    private var genresList: List<Genre> = emptyList()

    private var fetchGenresAttempts: Int = 0

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch { fetchGenres() }
        val filters = super.getFilterList().list.toMutableList()
        if (genresList.isNotEmpty()) {
            filters += GenreList(
                title = "Generos",
                genres = genresList,
            )
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Aperte 'Redefinir' mostrar os gêneros disponíveis"),
            )
        }
        return FilterList(filters)
    }

    private fun fetchGenres() {
        if (fetchGenresAttempts < 3 && genresList.isEmpty()) {
            try {
                genresList = client.newCall(GET(baseUrl, headers)).execute()
                    .use { parseGenres(it.asJsoup()) }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    private fun parseGenres(document: Document): List<Genre> {
        return document.select(".filters .filter:first-child input:not(.hidden)")
            .map { element ->
                Genre(element.attr("id"), element.attr("value"))
            }
    }
}
