package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter : Filter.Select<String>("Status", STATUS_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = STATUS_OPTIONS[state].second
}

class TypeFilter : Filter.Select<String>("Type", TYPE_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = TYPE_OPTIONS[state].second
}

class LanguageFilter : Filter.Select<String>("Language", LANG_OPTIONS.map { it.first }.toTypedArray()) {
    fun toValue(): String? = LANG_OPTIONS[state].second
}

class YearFilter : Filter.Text("Year")

class GenreFilter : Filter.Group<GenreOption>("Genres", GENRE_OPTIONS.map { GenreOption(it) }) {
    fun toGenres(): List<String> = state.filter { it.state }.map { it.name }
}

class GenreOption(name: String) : Filter.CheckBox(name)

private val STATUS_OPTIONS = arrayOf(
    "Any" to null,
    "Ongoing" to "ongoing",
    "Completed" to "completed",
    "Upcoming" to "upcoming",
    "Hiatus" to "hiatus",
    "Cancelled" to "cancelled",
)

private val TYPE_OPTIONS = arrayOf(
    "Any" to null,
    "Manga" to "JP",
    "Manhwa" to "KR",
    "Manhua" to "CN",
)

private val LANG_OPTIONS = arrayOf(
    "Any" to null,
    "English" to "en",
    "Arabic" to "ar",
    "Spanish" to "es",
    "Spanish (Latin America)" to "es-419",
    "French" to "fr",
    "Italian" to "it",
    "Polish" to "pl",
    "Portuguese (Brazil)" to "pt-br",
    "German" to "de",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Chinese" to "zh",
    "Russian" to "ru",
    "Turkish" to "tr",
    "Thai" to "th",
    "Vietnamese" to "vi",
    "Indonesian" to "id",
    "Malay" to "ms",
    "Tagalog" to "tl",
    "Hindi" to "hi",
    "Bengali" to "bn",
    "Urdu" to "ur",
    "Persian" to "fa",
    "Hebrew" to "he",
    "Dutch" to "nl",
    "Swedish" to "sv",
    "Norwegian" to "no",
    "Danish" to "da",
    "Finnish" to "fi",
    "Portuguese (Portugal)" to "pt",
    "Bulgarian" to "bg",
)

private val GENRE_OPTIONS = listOf(
    "Action",
    "Adventure",
    "Boys' Love",
    "Comedy",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Girls' Love",
    "Horror",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Supernatural",
    "Thriller",
    "Mystery",
    "Psychological",
    "Sports",
    "Music",
    "Josei",
    "Seinen",
    "Shoujo",
    "Shounen",
)
