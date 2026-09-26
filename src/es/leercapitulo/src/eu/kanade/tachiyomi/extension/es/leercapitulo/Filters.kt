package eu.kanade.tachiyomi.extension.es.leercapitulo

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    UriPartFilter(
        "Género",
        arrayOf(
            "Todos" to "",
            "Acción" to "action",
            "Aventura" to "adventure",
            "Boys' Love" to "boys-love",
            "Comedia" to "comedy",
            "Crimen" to "crime",
            "Drama" to "drama",
            "Fantasía" to "fantasy",
            "Girls' Love" to "girls-love",
            "Historia" to "historical",
            "Horror" to "horror",
            "Isekai" to "isekai",
            "Magical Girls" to "magical-girls",
            "Mecha" to "mecha",
            "Médico" to "medical",
            "Misterio" to "mystery",
            "Filosófico" to "philosophical",
            "Psicológico" to "psychological",
            "Romance" to "romance",
            "Ciencia ficción" to "sci-fi",
            "Recuentos de la vida" to "slice-of-life",
            "Deportes" to "sports",
            "Superhéroes" to "superhero",
            "Thriller" to "thriller",
            "Tragedia" to "tragedy",
            "Wuxia" to "wuxia",
            "Seinen" to "seinen",
            "Shounen" to "shounen",
            "Ecchi" to "ecchi",
            "Shoujo" to "shoujo",
            "Mature" to "mature",
            "Adult" to "adult",
            "Shounen Ai" to "shounen-ai",
            "Género Bender" to "gender-bender",
            "Shotacon" to "shotacon",
            "Josei" to "josei",
            "Yaoi" to "yaoi",
            "Smut" to "smut",
            "Ciberpunk" to "ciberpunk",
            "Vida Escolar" to "vida-escolar",
            "Realidad Virtual" to "realidad-virtual",
            "Fantasia" to "fantasia",
            "Yuri" to "yuri",
            "Sobrenatural" to "sobrenatural",
            "Magia" to "magia",
            "Guerra" to "guerra",
            "Policiaco" to "policiaco",
            "Artes Marciales" to "artes-marciales",
            "Gore" to "gore",
            "Superpoderes" to "superpoderes",
            "Familia" to "familia",
            "Supervivencia" to "supervivencia",
            "Demonios" to "demonios",
            "Realidad" to "realidad",
            "Telenovela" to "telenovela",
            "Parodia" to "parodia",
            "Traps" to "traps",
            "Militar" to "militar",
            "Música" to "musica",
            "Vampiros" to "vampiros",
            "Extranjero" to "extranjero",
            "Oeste" to "oeste",
            "Shoujo Ai" to "shoujo-ai",
            "Doujinshi" to "doujinshi",
            "Apocalíptico" to "apocaliptico",
            "Reencarnación" to "reencarnacion",
            "Niños" to "ninos",
            "Lolicon" to "lolicon",
            "Hentai" to "hentai",
            "Animación" to "animacion",
        ),
    )

class ThemeFilter :
    UriPartFilter(
        "Temática",
        arrayOf(
            "Todos" to "",
            "Aliens" to "aliens",
            "Animales" to "animals",
            "Cocina" to "cooking",
            "Cross-dressing" to "cross-dressing",
            "Delincuentes" to "delinquents",
            "Demonios" to "demons",
            "Cambio de género" to "genderswap",
            "Fantasmas" to "ghosts",
            "Gyaru" to "gyaru",
            "Harén" to "harem",
            "Incesto" to "incest",
            "Loli" to "loli",
            "Mafia" to "mafia",
            "Magia" to "magic",
            "Artes marciales" to "martial-arts",
            "Militar" to "military",
            "Monster Girls" to "monster-girls",
            "Monstruos" to "monsters",
            "Música" to "music",
            "Ninja" to "ninja",
            "Trabajadores de oficina" to "office-workers",
            "Policía" to "police",
            "Postapocalíptico" to "post-apocalyptic",
            "Reencarnación" to "reincarnation",
            "Harén inverso" to "reverse-harem",
            "Samurái" to "samurai",
            "Vida escolar" to "school-life",
            "Shota" to "shota",
            "Sobrenatural" to "supernatural",
            "Supervivencia" to "survival",
            "Viaje en el tiempo" to "time-travel",
            "Juegos tradicionales" to "traditional-games",
            "Vampiros" to "vampires",
            "Videojuegos" to "video-games",
            "Villana" to "villainess",
            "Realidad virtual" to "virtual-reality",
            "Zombis" to "zombies",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            "Todos" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Comic" to "comic",
        ),
    )

class SortFilter :
    UriPartFilter(
        "Orden",
        arrayOf(
            "Sin ordenar" to "",
            "A - Z" to "az",
            "Z - A" to "za",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Estado",
        arrayOf(
            "Todos" to "",
            "En emisión" to "ongoing",
            "Finalizado" to "completed",
            "En pausa" to "paused",
            "Cancelado" to "cancelled",
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun toUriPart(): String = vals[state].second
}
