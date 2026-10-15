package eu.kanade.tachiyomi.extension.id.ryukomik

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter(values: Array<String>) : Filter.Select<String>("Tipe Komik", values)
class StatusFilter(values: Array<String>) : Filter.Select<String>("Status", values)
class OrderByFilter(values: Array<String>) : Filter.Select<String>("Urutkan Berdasarkan", values)
class LetterFilter(values: Array<String>) : Filter.Select<String>("Huruf Awalan", values)
class GenreFilter(values: Array<String>) : Filter.Select<String>("Genre", values)

val TYPE_OPTIONS = arrayOf(
    "Semua" to "",
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
)

val STATUS_OPTIONS = arrayOf(
    "Semua" to "",
    "Ongoing (Berjalan)" to "ongoing",
    "Completed (Tamat)" to "end",
)

val ORDER_OPTIONS = arrayOf(
    "Default" to "",
    "Chapter Terbaru" to "modified",
    "Komik Terbaru" to "date",
    "Acak" to "rand",
)

val LETTER_OPTIONS = arrayOf(
    "Semua" to "",
    "#" to "%23",
    "A" to "A",
    "B" to "B",
    "C" to "C",
    "D" to "D",
    "E" to "E",
    "F" to "F",
    "G" to "G",
    "H" to "H",
    "I" to "I",
    "J" to "J",
    "K" to "K",
    "L" to "L",
    "M" to "M",
    "N" to "N",
    "O" to "O",
    "P" to "P",
    "Q" to "Q",
    "R" to "R",
    "S" to "S",
    "T" to "T",
    "U" to "U",
    "V" to "V",
    "W" to "W",
    "X" to "X",
    "Y" to "Y",
    "Z" to "Z",
)

val GENRE_OPTIONS = arrayOf(
    "Semua" to "",
    "Action" to "action",
    "Adventure" to "adventure",
    "Boys' Love" to "boys'-love",
    "Comedy" to "comedy",
    "Crime" to "crime",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Girls' Love" to "girls'-love",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Magical Girls" to "magical-girls",
    "Martial Arts" to "martial-arts",
    "Mecha" to "mecha",
    "Medical" to "medical",
    "Music" to "music",
    "Mystery" to "mystery",
    "Philosophical" to "philosophical",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-Fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shoujo Ai" to "shoujo-ai",
    "Shounen" to "shounen",
    "Shounen Ai" to "shounen-ai",
    "Slice of Life" to "slice-of-life",
    "Sports" to "sports",
    "Superhero" to "superhero",
    "Supernatural" to "supernatural",
    "Thriller" to "thriller",
    "Tragedy" to "tragedy",
    "Wuxia" to "wuxia",
    "Yuri" to "yuri",
)
