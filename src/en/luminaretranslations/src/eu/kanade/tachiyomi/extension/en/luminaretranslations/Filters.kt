package eu.kanade.tachiyomi.extension.en.luminaretranslations

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxItem(name: String, val slug: String) : Filter.CheckBox(name)

abstract class SlugGroupFilter(name: String, entries: List<Filters>) : Filter.Group<CheckBoxItem>(name, entries.map { CheckBoxItem(it.name, it.slug) })

class GenreFilter(genres: List<Filters>) : SlugGroupFilter("Genres", genres)

class TagFilter(tags: List<Filters>) : SlugGroupFilter("Tags", tags)

abstract class SlugSelectFilter(name: String, entries: List<Filters>) : Filter.Select<String>(name, arrayOf("Any") + entries.map { it.name }.toTypedArray()) {
    private val slugs = listOf("") + entries.map { it.slug }
    val selected get() = slugs[state]
}

class AuthorFilter(authors: List<Filters>) : SlugSelectFilter("Author", authors)

class ArtistFilter(artists: List<Filters>) : SlugSelectFilter("Artist", artists)

abstract class TypeStatusSortFilter(name: String, entries: List<Filters>, withAny: Boolean = false) : Filter.Select<String>(name, (if (withAny) arrayOf("Any") else emptyArray()) + entries.map { it.slug }.toTypedArray()) {
    private val options = (if (withAny) listOf("") else emptyList()) + entries.map { it.name }
    val selected get() = options[state]
}

class SortFilter(sorts: List<Filters>) : TypeStatusSortFilter("Sort", sorts)

class TypeFilter(types: List<Filters>) : TypeStatusSortFilter("Type", types, true)

class StatusFilter(statuses: List<Filters>) : TypeStatusSortFilter("Status", statuses, true)
