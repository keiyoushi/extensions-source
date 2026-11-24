package eu.kanade.tachiyomi.extension.es.spnmanga

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

open class UriPartFilter(
    displayName: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray(), state) {
    fun addTo(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

class SortFilter : UriPartFilter("Sort", "sort", SORTS, SORTS.indexOfFirst { it.second == "popular" })

class GenreFilter : UriPartFilter("Genre", "genre", GENRES)

class ThemeFilter : UriPartFilter("Theme", "theme", THEMES)

class TypeFilter : UriPartFilter("Type", "type", TYPES)

class StatusFilter : UriPartFilter("Status", "status", STATUSES)

private val SORTS = listOf(
    "A → Z" to "az",
    "Z → A" to "za",
    "Latest update" to "latest",
    "Most popular" to "popular",
)

private val GENRES = listOf(
    "All genres" to "",
    "Action" to "action",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Crime" to "crime",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Magical Girls" to "magical-girls",
    "Mecha" to "mecha",
    "Medical" to "medical",
    "Mystery" to "mystery",
    "Philosophical" to "philosophical",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "Sci-Fi" to "sci-fi",
    "Slice of Life" to "slice-of-life",
    "Sports" to "sports",
    "Superhero" to "superhero",
    "Thriller" to "thriller",
    "Tragedy" to "tragedy",
    "Wuxia" to "wuxia",
    "Seinen" to "seinen",
    "Shounen" to "shounen",
    "Ecchi" to "ecchi",
    "Shoujo" to "shoujo",
    "Mature" to "mature",
    "Adult" to "adult",
    "Shounen Ai" to "shounen-ai",
    "Gender Bender" to "gender-bender",
    "Shotacon" to "shotacon",
    "Josei" to "josei",
    "Yaoi" to "yaoi",
    "Smut" to "smut",
    "Ciberpunk" to "ciberpunk",
    "Vida Escolar" to "vida-escolar",
    "Realidad Virtual" to "realidad-virtual",
    "Fantasia" to "fantasia",
    "Comedia" to "comedia",
    "Recuentos de la vida" to "recuentos-de-la-vida",
    "Yuri" to "yuri",
    "Sobrenatural" to "sobrenatural",
    "Magia" to "magia",
    "Tragedia" to "tragedia",
    "Historia" to "historia",
    "Guerra" to "guerra",
    "Misterio" to "misterio",
    "Policiaco" to "policiaco",
    "Artes Marciales" to "artes-marciales",
    "Gore" to "gore",
    "Superpoderes" to "superpoderes",
    "Familia" to "familia",
    "Aventura" to "aventura",
    "Supervivencia" to "supervivencia",
    "Demonios" to "demonios",
    "Realidad" to "realidad",
    "Telenovela" to "telenovela",
    "Crimen" to "crimen",
    "Parodia" to "parodia",
    "Deporte" to "deporte",
    "Traps" to "traps",
    "Militar" to "militar",
    "Musica" to "musica",
    "Vampiros" to "vampiros",
    "Extranjero" to "extranjero",
    "Oeste" to "oeste",
    "Shoujo Ai" to "shoujo-ai",
    "Doujinshi" to "doujinshi",
    "Psicológico" to "psicologico",
    "Acción" to "accion",
    "Ciencia Ficción" to "ciencia-ficcion",
    "Género Bender" to "genero-bender",
    "Apocalíptico" to "apocaliptico",
    "Reencarnación" to "reencarnacion",
    "Niños" to "ninos",
    "Lolicon" to "lolicon",
    "Hentai" to "hentai",
    "Animación" to "animacion",
)

private val THEMES = listOf(
    "All themes" to "",
    "Aliens" to "aliens",
    "Animals" to "animals",
    "Cooking" to "cooking",
    "Cross-dressing" to "cross-dressing",
    "Delinquents" to "delinquents",
    "Demons" to "demons",
    "Genderswap" to "genderswap",
    "Ghosts" to "ghosts",
    "Gyaru" to "gyaru",
    "Harem" to "harem",
    "Incest" to "incest",
    "Loli" to "loli",
    "Mafia" to "mafia",
    "Magic" to "magic",
    "Martial Arts" to "martial-arts",
    "Military" to "military",
    "Monster Girls" to "monster-girls",
    "Monsters" to "monsters",
    "Music" to "music",
    "Ninja" to "ninja",
    "Office Workers" to "office-workers",
    "Police" to "police",
    "Post-Apocalyptic" to "post-apocalyptic",
    "Reincarnation" to "reincarnation",
    "Reverse Harem" to "reverse-harem",
    "Samurai" to "samurai",
    "School Life" to "school-life",
    "Shota" to "shota",
    "Supernatural" to "supernatural",
    "Survival" to "survival",
    "Time Travel" to "time-travel",
    "Traditional Games" to "traditional-games",
    "Vampires" to "vampires",
    "Video Games" to "video-games",
    "Villainess" to "villainess",
    "Virtual Reality" to "virtual-reality",
    "Zombies" to "zombies",
)

private val TYPES = listOf(
    "All types" to "",
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Comic" to "comic",
)

private val STATUSES = listOf(
    "Any status" to "",
    "Completed" to "completed",
    "Ongoing" to "ongoing",
    "Paused" to "paused",
    "Cancelled" to "cancelled",
)
