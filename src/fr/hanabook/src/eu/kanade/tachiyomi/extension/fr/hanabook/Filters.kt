package eu.kanade.tachiyomi.extension.fr.hanabook

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.OkHttpClient
import kotlin.concurrent.thread

class TagFilter(name: String, val id: Int) : Filter.CheckBox(name)

class AboFilter : Filter.CheckBox("Abonnement uniquement")

class GenresFilter(g: List<TagFilter>) : Filter.Group<TagFilter>("Genres", g)
class CollectionsFilter(c: List<TagFilter>) : Filter.Group<TagFilter>("Collections", c)
class AgesFilter(a: List<TagFilter>) : Filter.Group<TagFilter>("Âges", a)
class TypesFilter(t: List<TagFilter>) : Filter.Group<TagFilter>("Types", t)

fun getGlobalFilterList(apiUrl: String, client: OkHttpClient, headers: Headers): FilterList {
    fetchFilters(apiUrl, client, headers)
    val filters = mutableListOf<Filter<*>>(
        AboFilter(),
    )

    if (filtersState == FiltersState.FETCHED) {
        filters += listOf(
            GenresFilter(genresList),
            CollectionsFilter(collectionsList),
            AgesFilter(agesList),
            TypesFilter(typesList),
        )
    } else {
        filters += Filter.Header("Appuyer sur 'Réinitialiser' pour charger les filtres")
    }

    return FilterList(filters)
}

private var genresList: List<TagFilter> = emptyList()
private var collectionsList: List<TagFilter> = emptyList()
private var agesList: List<TagFilter> = emptyList()
private var typesList: List<TagFilter> = emptyList()
private var fetchFiltersAttempts = 0
private var filtersState = FiltersState.NOT_FETCHED

private fun fetchFilters(apiUrl: String, client: OkHttpClient, headers: Headers) {
    if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
    filtersState = FiltersState.FETCHING
    fetchFiltersAttempts++
    thread {
        try {
            val data = client.newCall(GET("$apiUrl/catalogue/filters/?filterAbo=false", headers))
                .execute()
                .parseAs<FiltersResponse>(transform = ::stripXssi)

            genresList = data.genres.map { TagFilter(it.nom, it.id) }
            collectionsList = data.collections.map { TagFilter(it.nom, it.id) }
            agesList = data.ages.map { TagFilter(it.age, it.id) }
            typesList = data.types.map { TagFilter(it.nom, it.id) }

            filtersState = FiltersState.FETCHED
        } catch (_: Throwable) {
            filtersState = FiltersState.NOT_FETCHED
        }
    }
}

private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }
