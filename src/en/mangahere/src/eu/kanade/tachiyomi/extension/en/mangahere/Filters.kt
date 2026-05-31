package eu.kanade.tachiyomi.extension.en.mangahere

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: Int) : Filter.TriState(name)

class TypeList(types: Array<String>) : Filter.Select<String>("Type", types, 1)
class CompletionList(completions: Array<String>) : Filter.Select<String>("Completed series", completions, 0)
class RatingList(ratings: Array<String>) : Filter.Select<String>("Minimum rating", ratings, 0)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class ArtistFilter(name: String) : Filter.Text(name)
class AuthorFilter(name: String) : Filter.Text(name)
class YearFilter(name: String) : Filter.Text(name)

val types = hashMapOf(
    "Japanese Manga" to 1,
    "Korean Manhwa" to 2,
    "Chinese Manhua" to 3,
    "European Manga" to 4,
    "American Manga" to 5,
    "Hong Kong Manga" to 6,
    "Other Manga" to 7,
    "Any" to 0,
)

val completions = arrayOf("Either", "No", "Yes")
val ratings = arrayOf("No Stars", "1 Star", "2 Stars", "3 Stars", "4 Stars", "5 Stars")

fun genres() = arrayListOf(
    Genre("Action", 1),
    Genre("Adventure", 2),
    Genre("Comedy", 3),
    Genre("Fantasy", 4),
    Genre("Historical", 5),
    Genre("Horror", 6),
    Genre("Martial Arts", 7),
    Genre("Mystery", 8),
    Genre("Romance", 9),
    Genre("Shounen Ai", 10),
    Genre("Supernatural", 11),
    Genre("Drama", 12),
    Genre("Shounen", 13),
    Genre("School Life", 14),
    Genre("Shoujo", 15),
    Genre("Gender Bender", 16),
    Genre("Josei", 17),
    Genre("Psychological", 18),
    Genre("Seinen", 19),
    Genre("Slice of Life", 20),
    Genre("Sci-fi", 21),
    Genre("Ecchi", 22),
    Genre("Harem", 23),
    Genre("Shoujo Ai", 24),
    Genre("Yuri", 25),
    Genre("Mature", 26),
    Genre("Tragedy", 27),
    Genre("Yaoi", 28),
    Genre("Doujinshi", 29),
    Genre("Sports", 30),
    Genre("Adult", 31),
    Genre("One Shot", 32),
    Genre("Smut", 33),
    Genre("Mecha", 34),
    Genre("Shotacon", 35),
    Genre("Lolicon", 36),
    Genre("Webtoons", 37),
)
