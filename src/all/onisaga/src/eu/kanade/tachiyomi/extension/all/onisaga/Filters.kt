package eu.kanade.tachiyomi.extension.all.onisaga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.text.Normalizer

fun getFilters() = FilterList(
    SortFilter(),
    TypeFilter(),
    FormatFilter(),
    StatusFilter(),
    DemographicFilter(),
    RatingFilter(),
    GenreFilter(),
    Filter.Separator(),
    Filter.Header("Themes and tags: names separated by commas, with - before a name to exclude it"),
    ThemeFilter(),
    TagFilter(),
    YearFromFilter(),
    YearToFilter(),
)

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class Option(name: String, val value: String) : Filter.CheckBox(name)

open class OptionsFilter(name: String, options: List<Pair<String, String>>) : Filter.Group<Option>(name, options.map { Option(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.value }.ifEmpty { null }
}

class TypeFilter :
    OptionsFilter(
        "Type",
        listOf(
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Comic" to "comic",
            "Other Asian" to "other_asian",
            "OEL" to "oel",
            "Other" to "other",
        ),
    )

class FormatFilter :
    OptionsFilter(
        "Format",
        listOf(
            "Serial" to "serial",
            "Oneshot" to "oneshot",
            "Anthology" to "anthology",
            "Doujinshi" to "doujinshi",
            "Artbook" to "artbook",
        ),
    )

class StatusFilter :
    OptionsFilter(
        "Status",
        listOf(
            "Ongoing" to "releasing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
            "Announced" to "announced",
            "Unknown" to "unknown",
        ),
    )

class DemographicFilter :
    OptionsFilter(
        "Demographic",
        listOf(
            "Shounen" to "shounen",
            "Shoujo" to "shoujo",
            "Seinen" to "seinen",
            "Josei" to "josei",
            "Kodomo" to "kodomo",
        ),
    )

class RatingFilter :
    OptionsFilter(
        "Content rating",
        listOf(
            "Safe" to "safe",
            "Suggestive" to "suggestive",
            "Mature" to "mature",
            "Explicit" to "explicit",
        ),
    )

class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter : Filter.Group<Genre>("Genres", GENRES.map { Genre(it.first, it.second) }) {
    val terms get() = terms(state.filter { it.isIncluded() }.map { it.slug }, state.filter { it.isExcluded() }.map { it.slug })
}

open class TermsFilter(name: String) : Filter.Text(name) {
    val terms: Terms?
        get() {
            val (exclude, include) = state.split(',').map { it.trim() }.filter { it.isNotEmpty() }.partition { it.startsWith('-') }
            return terms(include.map { it.toSlug() }, exclude.map { it.removePrefix("-").toSlug() })
        }
}

class ThemeFilter : TermsFilter("Themes")

class TagFilter : TermsFilter("Tags")

open class YearFilter(name: String) : Filter.Text(name) {
    val year get() = state.trim().toIntOrNull()
}

class YearFromFilter : YearFilter("From year")

class YearToFilter : YearFilter("To year")

private fun terms(include: List<String>, exclude: List<String>) = if (include.isEmpty() && exclude.isEmpty()) null else Terms(include, exclude)

// The site's slugs are its names without accents, in lowercase, with dashes
private fun String.toSlug() = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(DIACRITICS_REGEX, "")
    .lowercase()
    .replace(NON_SLUG_REGEX, "-")
    .trim('-')

private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
private val NON_SLUG_REGEX = Regex("[^a-z0-9]+")

private val SORTS = listOf(
    "Relevance" to "relevance",
    "Latest update" to "updated",
    "Newest" to "new",
    "First published" to "published",
    "Title" to "title",
)

private val GENRES = listOf(
    "Action" to "action",
    "Adventure" to "adventure",
    "Boys' Love" to "boys-love",
    "Comedy" to "comedy",
    "Cooking" to "cooking",
    "Crime" to "crime",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Gender Bender" to "gender-bender",
    "Girls' Love" to "girls-love",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Magical Girls" to "magical-girls",
    "Martial Arts" to "martial-arts",
    "Mecha" to "mecha",
    "Medical" to "medical",
    "Music" to "music",
    "Mystery" to "mystery",
    "Philosophical" to "philosophical",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "Science Fiction" to "science-fiction",
    "Slice of Life" to "slice-of-life",
    "Sports" to "sports",
    "Superhero" to "superhero",
    "Supernatural" to "supernatural",
    "Thriller" to "thriller",
    "Tragedy" to "tragedy",
    "Wuxia" to "wuxia",
)
