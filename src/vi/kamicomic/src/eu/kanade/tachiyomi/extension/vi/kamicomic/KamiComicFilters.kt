package eu.kanade.tachiyomi.extension.vi.kamicomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    GenreFilter(getGenreList()),
)

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

private fun getGenreList(): List<Genre> = listOf(
    Genre("15+", "15"),
    Genre("18+", "18"),
    Genre("3P", "3p"),
    Genre("ABO", "abo"),
    Genre("Action", "action"),
    Genre("Adaptation", "adaptation"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Age Gap", "age-gap"),
    Genre("Animals", "animals"),
    Genre("BDSM", "bdsm"),
    Genre("BL", "bl"),
    Genre("Cheating/Infidelity", "cheating-infidelity"),
    Genre("Childhood Friends", "childhood-friends"),
    Genre("Comedy", "comedy"),
    Genre("Completed", "completed"),
    Genre("Crime", "crime"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Ghosts", "ghosts"),
    Genre("GL", "gl"),
    Genre("Harem", "harem"),
    Genre("Hentai", "hentai"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Magic", "magic"),
    Genre("Manga", "manga"),
    Genre("Manhua", "manhua"),
    Genre("Manhwa", "manhwa"),
    Genre("Mature", "mature"),
    Genre("Mystery", "mystery"),
    Genre("Netorare/NTR", "netorare-ntr"),
    Genre("Non-Human", "non-human"),
    Genre("Oneshot", "oneshot"),
    Genre("Psychological", "psychological"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Revenge", "revenge"),
    Genre("Reverse Harem", "reverse-harem"),
    Genre("Romance", "romance"),
    Genre("Showbiz", "showbiz"),
    Genre("Smut", "smut"),
    Genre("Thriller", "thriller"),
    Genre("Tragedy", "tragedy"),
    Genre("Uncensored", "uncensored"),
    Genre("Violence", "violence"),
    Genre("Webtoon", "webtoon"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)
