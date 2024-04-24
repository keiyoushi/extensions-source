package eu.kanade.tachiyomi.multisrc.galleryadults

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val uri: String) : Filter.CheckBox(name)
class GenresFilter(genres: List<Genre>) : Filter.Group<Genre>(
    "Tags",
    genres.map { Genre(it.name, it.uri) },
)

class SortOrderFilter(sortOrderURIs: List<Pair<String, String>>) :
    Filter.Select<String>("Sort By", sortOrderURIs.map { it.first }.toTypedArray())

class FavoriteFilter : Filter.CheckBox("Show favorites only (login via WebView)", false)

// Speechless
class SpeechlessFilter : Filter.CheckBox("Show speechless items only", false)

// Intermediate search
class SearchFlagFilter(name: String, val uri: String, state: Boolean = true) : Filter.CheckBox(name, state)
class CategoryFilters(flags: List<SearchFlagFilter>) : Filter.Group<SearchFlagFilter>("Categories", flags)

// Advance search
class TagsFilter : Filter.Text("Tags")
class ParodiesFilter : Filter.Text("Parodies")
class ArtistsFilter : Filter.Text("Artists")
class CharactersFilter : Filter.Text("Characters")
class GroupsFilter : Filter.Text("Groups")
