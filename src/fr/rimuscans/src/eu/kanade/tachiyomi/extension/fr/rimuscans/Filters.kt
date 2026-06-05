package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.OkHttpClient
import kotlin.concurrent.thread

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Trier par",
        arrayOf(
            "Dernière mise à jour" to "updated",
            "Note" to "rating",
            "Nombre de chapitres" to "chapters",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "Tous" to "",
            "Manhwa" to "webtoon",
            "Manga" to "manga",
        ),
    )

class StatusCheckBox(name: String, val value: String) : Filter.CheckBox(name)
class StatusFilter :
    Filter.Group<Filter.CheckBox>(
        "Statut",
        listOf(
            StatusCheckBox("En cours", "ongoing"),
            StatusCheckBox("Terminé", "completed"),
            StatusCheckBox("En pause", "hiatus"),
        ),
    )

class PremiumOnlyFilter : Filter.CheckBox("Premium uniquement", false)

class MinChaptersFilter :
    UriPartFilter(
        "Minimum de chapitres",
        arrayOf(
            "Tous" to "",
            "10+" to "10",
            "50+" to "50",
            "100+" to "100",
            "200+" to "200",
            "300+" to "300",
            "500+" to "500",
        ),
    )

class GenreCheckBox(name: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<String>) : Filter.Group<Filter.CheckBox>("Genres", genres.map { GenreCheckBox(it) })

// ============================ Genre fetching ============================

private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

private var genresList: List<String> = emptyList()
private var filtersState = FiltersState.NOT_FETCHED
private var fetchAttempts = 0

fun getRimuFilterList(baseUrl: String, client: OkHttpClient, headers: Headers): FilterList {
    fetchGenres(baseUrl, client, headers)
    val filters = mutableListOf<Filter<*>>(
        Filter.Header("Les filtres sont ignorés par la recherche texte"),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        MinChaptersFilter(),
        PremiumOnlyFilter(),
    )
    if (filtersState == FiltersState.FETCHED) {
        filters += GenreFilter(genresList)
    } else {
        filters += Filter.Header("Appuyez sur « Réinitialiser » pour charger les genres")
    }
    return FilterList(filters)
}

private fun fetchGenres(baseUrl: String, client: OkHttpClient, headers: Headers) {
    if (filtersState != FiltersState.NOT_FETCHED || fetchAttempts >= 3) return
    filtersState = FiltersState.FETCHING
    fetchAttempts++
    thread {
        try {
            val response = client.newCall(GET("$baseUrl/api/admin/genres", headers)).execute()
            genresList = response.parseAs<GenresDto>().genres
            filtersState = FiltersState.FETCHED
        } catch (_: Throwable) {
            filtersState = FiltersState.NOT_FETCHED
        }
    }
}
