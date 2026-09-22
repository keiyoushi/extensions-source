package eu.kanade.tachiyomi.extension.es.leercapitulo

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    UriPartFilter(
        "Género",
        arrayOf(
            Pair("Todos", ""),
            Pair("Acción", "action"),
            Pair("Aventura", "adventure"),
            Pair("Boys' Love", "boys-love"),
            Pair("Comedia", "comedy"),
            Pair("Crimen", "crime"),
            Pair("Drama", "drama"),
            Pair("Fantasía", "fantasy"),
            Pair("Girls' Love", "girls-love"),
            Pair("Historia", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Magical Girls", "magical-girls"),
            Pair("Mecha", "mecha"),
            Pair("Médico", "medical"),
            Pair("Misterio", "mystery"),
            Pair("Filosófico", "philosophical"),
            Pair("Psicológico", "psychological"),
            Pair("Romance", "romance"),
            Pair("Ciencia ficción", "sci-fi"),
            Pair("Recuentos de la vida", "slice-of-life"),
            Pair("Deportes", "sports"),
            Pair("Superhéroes", "superhero"),
            Pair("Thriller", "thriller"),
            Pair("Tragedia", "tragedy"),
            Pair("Wuxia", "wuxia"),
            Pair("Seinen", "seinen"),
            Pair("Shounen", "shounen"),
            Pair("Ecchi", "ecchi"),
            Pair("Shoujo", "shoujo"),
            Pair("Mature", "mature"),
            Pair("Adult", "adult"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Género Bender", "gender-bender"),
            Pair("Shotacon", "shotacon"),
            Pair("Josei", "josei"),
            Pair("Yaoi", "yaoi"),
            Pair("Smut", "smut"),
            Pair("Ciberpunk", "ciberpunk"),
            Pair("Vida Escolar", "vida-escolar"),
            Pair("Realidad Virtual", "realidad-virtual"),
            Pair("Fantasia", "fantasia"),
            Pair("Comedia", "comedia"),
            Pair("Yuri", "yuri"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("Magia", "magia"),
            Pair("Tragedia", "tragedia"),
            Pair("Guerra", "guerra"),
            Pair("Policiaco", "policiaco"),
            Pair("Artes Marciales", "artes-marciales"),
            Pair("Gore", "gore"),
            Pair("Superpoderes", "superpoderes"),
            Pair("Familia", "familia"),
            Pair("Supervivencia", "supervivencia"),
            Pair("Demonios", "demonios"),
            Pair("Realidad", "realidad"),
            Pair("Telenovela", "telenovela"),
            Pair("Parodia", "parodia"),
            Pair("Traps", "traps"),
            Pair("Militar", "militar"),
            Pair("Música", "musica"),
            Pair("Vampiros", "vampiros"),
            Pair("Extranjero", "extranjero"),
            Pair("Oeste", "oeste"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Acción", "accion"),
            Pair("Ciencia Ficción", "ciencia-ficcion"),
            Pair("Apocalíptico", "apocaliptico"),
            Pair("Reencarnación", "reencarnacion"),
            Pair("Niños", "ninos"),
            Pair("Lolicon", "lolicon"),
            Pair("Hentai", "hentai"),
            Pair("Animación", "animacion"),
        ),
    )

class ThemeFilter :
    UriPartFilter(
        "Temática",
        arrayOf(
            Pair("Todos", ""),
            Pair("Aliens", "aliens"),
            Pair("Animales", "animals"),
            Pair("Cocina", "cooking"),
            Pair("Cross-dressing", "cross-dressing"),
            Pair("Delincuentes", "delinquents"),
            Pair("Demonios", "demons"),
            Pair("Cambio de género", "genderswap"),
            Pair("Fantasmas", "ghosts"),
            Pair("Gyaru", "gyaru"),
            Pair("Harén", "harem"),
            Pair("Incesto", "incest"),
            Pair("Loli", "loli"),
            Pair("Mafia", "mafia"),
            Pair("Magia", "magic"),
            Pair("Artes marciales", "martial-arts"),
            Pair("Militar", "military"),
            Pair("Monster Girls", "monster-girls"),
            Pair("Monstruos", "monsters"),
            Pair("Música", "music"),
            Pair("Ninja", "ninja"),
            Pair("Trabajadores de oficina", "office-workers"),
            Pair("Policía", "police"),
            Pair("Postapocalíptico", "post-apocalyptic"),
            Pair("Reencarnación", "reincarnation"),
            Pair("Harén inverso", "reverse-harem"),
            Pair("Samurái", "samurai"),
            Pair("Vida escolar", "school-life"),
            Pair("Shota", "shota"),
            Pair("Sobrenatural", "supernatural"),
            Pair("Supervivencia", "survival"),
            Pair("Viaje en el tiempo", "time-travel"),
            Pair("Juegos tradicionales", "traditional-games"),
            Pair("Vampiros", "vampires"),
            Pair("Videojuegos", "video-games"),
            Pair("Villana", "villainess"),
            Pair("Realidad virtual", "virtual-reality"),
            Pair("Zombis", "zombies"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            Pair("Todos", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Comic", "comic"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Orden",
        arrayOf(
            Pair("Sin ordenar", ""),
            Pair("A - Z", "az"),
            Pair("Z - A", "za"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Estado",
        arrayOf(
            Pair("Todos", ""),
            Pair("En emisión", "ongoing"),
            Pair("Finalizado", "completed"),
            Pair("En pausa", "paused"),
            Pair("Cancelado", "cancelled"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun toUriPart() = vals[state].second
}
