package eu.kanade.tachiyomi.extension.fr.hanabook

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class TagFilter(name: String, val id: Int) : Filter.CheckBox(name)

class AboFilter : Filter.CheckBox("Abonnement uniquement")

class GenresFilter(g: List<TagFilter>) : Filter.Group<TagFilter>("Genres", g)
class CollectionsFilter(c: List<TagFilter>) : Filter.Group<TagFilter>("Collections", c)
class AgesFilter(a: List<TagFilter>) : Filter.Group<TagFilter>("Âges", a)
class TypesFilter(t: List<TagFilter>) : Filter.Group<TagFilter>("Types", t)

fun getGlobalFilterList(data: FiltersResponse?): FilterList {
    val filters = mutableListOf<Filter<*>>(
        AboFilter(),
    )

    if (data != null) {
        filters += listOf(
            GenresFilter(data.genres.map { TagFilter(it.nom, it.id) }),
            CollectionsFilter(data.collections.map { TagFilter(it.nom, it.id) }),
            AgesFilter(data.ages.map { TagFilter(it.age, it.id) }),
            TypesFilter(data.types.map { TagFilter(it.nom, it.id) }),
        )
    }

    return FilterList(filters)
}
