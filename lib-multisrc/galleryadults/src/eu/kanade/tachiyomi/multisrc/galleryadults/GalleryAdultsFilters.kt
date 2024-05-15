package eu.kanade.tachiyomi.multisrc.galleryadults

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val uri: String) : Filter.CheckBox(name)
class GenresFilter(genres: Map<String, String>) : Filter.Group<Genre>(
    "Tags",
    genres.map { Genre(it.key, it.value) },
)

class SortOrderFilter(sortOrderURIs: List<Pair<String, String>>) :
    Filter.Select<String>("Sort By", sortOrderURIs.map { it.first }.toTypedArray())

class FavoriteFilter : Filter.CheckBox("Show favorites only (login via WebView)", false)

class RandomEntryFilter : Filter.CheckBox("Random manga", false)

// Speechless
class SpeechlessFilter : Filter.CheckBox("Show speechless items only", false)

// Intermediate search
class SearchFlagFilter(name: String, val uri: String, state: Boolean = true) : Filter.CheckBox(name, state)
class CategoryFilters(flags: List<SearchFlagFilter>) : Filter.Group<SearchFlagFilter>("Categories", flags)

// Advance search
abstract class AdvancedTextFilter(name: String) : Filter.Text(name)
class TagsFilter : AdvancedTextFilter("Tags")
class ParodiesFilter : AdvancedTextFilter("Parodies")
class ArtistsFilter : AdvancedTextFilter("Artists")
class CharactersFilter : AdvancedTextFilter("Characters")
class GroupsFilter : AdvancedTextFilter("Groups")
