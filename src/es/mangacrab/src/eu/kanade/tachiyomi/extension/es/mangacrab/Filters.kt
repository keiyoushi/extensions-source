package eu.kanade.tachiyomi.extension.es.mangacrab

import eu.kanade.tachiyomi.source.model.Filter

class FeedFilter :
    Filter.Select<String>(
        name = "Orden",
        values = arrayOf(
            "Descubre",
            "Nuevos",
            "Actualizados",
        ),
    ) {
    val selectedValue: String
        get() = when (state) {
            1 -> "new"
            2 -> "updated"
            else -> "discover"
        }
}

class RankingFilter :
    Filter.Select<String>(
        name = "Ranking",
        values = arrayOf(
            "Sin ranking",
            "Vistas diarias",
            "Vistas semanales",
            "Vistas mensuales",
        ),
    ) {
    val selectedValue: String
        get() = when (state) {
            1 -> "daily"
            2 -> "weekly"
            3 -> "monthly"
            else -> ""
        }
}

class StatusFilter :
    Filter.Select<String>(
        name = "Estado",
        values = arrayOf(
            "Todos",
            "En curso",
            "Finalizado",
            "Cancelado",
            "Hiato",
            "Próximo",
        ),
    ) {
    val selectedValue: String
        get() = when (state) {
            1 -> "on-going"
            2 -> "end"
            3 -> "canceled"
            4 -> "on-hold"
            5 -> "upcoming"
            else -> ""
        }
}

class OriginFilter :
    Filter.Select<String>(
        name = "Origen",
        values = arrayOf(
            "Todos",
            "Manga",
            "Manhua",
            "Manhwa",
        ),
    ) {
    val selectedValue: String
        get() = when (state) {
            1 -> "manga"
            2 -> "manhua"
            3 -> "manhwa"
            else -> ""
        }
}

class GenreFilter :
    Filter.Select<String>(
        name = "Género",
        values = GENRES.map { it.first }.toTypedArray(),
    ) {

    val selectedValue: String
        get() = GENRES[state].second

    private companion object {
        val GENRES = arrayOf(
            "Todos" to "",
            "+15" to "15",
            "Academia" to "academia",
            "acccion" to "acccion",
            "Acción" to "accion",
            "Action" to "action",
            "Adventure" to "adventure",
            "Aliado" to "aliado",
            "Amor" to "amor",
            "Ángeles" to "angeles",
            "Animación" to "animacion",
            "Anti-heroe" to "anti-heroe",
            "Apocalipsis" to "apocalipsis",
            "Apocalíptico" to "apocaliptico",
            "Apocalipto" to "apocalipto",
            "Artes marcial" to "artes-marcial",
            "Artes Marciales" to "artes-marciales",
            "Aventura" to "aventura",
            "Aventura Drama" to "aventura-drama",
            "Bestias invocadas" to "bestias-invocadas",
            "Caballeros" to "caballeros",
            "Cartoon" to "cartoon",
            "Cash" to "cash",
            "Cazador" to "cazador",
            "Ciencia Ficción" to "ciencia-ficcion",
            "Combate" to "combate",
            "Comedia" to "comedia",
            "Comedy" to "comedy",
            "Comida" to "comida",
            "Conspiracion" to "conspiracion",
            "Contrato" to "contrato",
            "Corrupción" to "corrupcion",
            "Creador de ciudades" to "creador-de-ciudades",
            "Crimen" to "crimen",
            "Cultivación" to "cultivacion",
            "Cultivo" to "cultivo",
            "Delincuentes" to "delincuentes",
            "Demonio" to "demonio",
            "Demonios" to "demonios",
            "Deporte" to "deporte",
            "Detective" to "detective",
            "Dinastía Joseon" to "dinastia-joseon",
            "Dioses" to "dioses",
            "Domador de bestias" to "domador-de-bestias",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Erotico" to "erotico",
            "Escolar" to "escolar",
            "Espíritus" to "espiritus",
            "estrategia" to "estrategia",
            "Evolución" to "evolucion",
            "Exclusivo" to "exclusivo",
            "Familia" to "familia",
            "Familia Real" to "familia-real",
            "Fantansía" to "fantansia",
            "Fantasia" to "fantasia",
            "Fantasía moderna" to "fantasia-moderna",
            "Fantasy" to "fantasy",
            "Favoritos" to "favoritos",
            "Game" to "game",
            "Género Bender" to "genero-bender",
            "Gore" to "gore",
            "Guerra" to "guerra",
            "Habilidades" to "habilidades",
            "Harem" to "harem",
            "Harem Inverso" to "harem-inverso",
            "Haren" to "haren",
            "Hentai" to "hentai",
            "Hermes" to "hermes",
            "Heroe" to "heroe",
            "Heroe Villano" to "heroe-villano",
            "Historia" to "historia",
            "Historical" to "historical",
            "Historico" to "historico",
            "Horror" to "horror",
            "Inmersión" to "inmersion",
            "Intriga" to "intriga",
            "Inversiones" to "inversiones",
            "Invocación" to "invocacion",
            "Isekai" to "isekai",
            "Juego" to "juego",
            "Juego en Linea" to "juego-en-linea",
            "Ladies" to "ladies",
            "Madrastra" to "madrastra",
            "Magia" to "magia",
            "Magnate" to "magnate",
            "Maldad" to "maldad",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Manhwa" to "manhwa",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Mazmorras" to "mazmorras",
            "MC" to "mc",
            "mc chambeador" to "mc-chambeador",
            "MC inteligente" to "mc-inteligente",
            "mc medico" to "mc-medico",
            "MC OP" to "mc-op",
            "Mecha" to "mecha",
            "Medicina" to "medicina",
            "Medieval" to "medieval",
            "Meian" to "meian",
            "Milf" to "milf",
            "Militar" to "militar",
            "Misterio" to "misterio",
            "Monstruo" to "monstruo",
            "Monstruos" to "monstruos",
            "Muchas Waifus" to "muchas-waifus",
            "Mujer casada" to "mujer-casada",
            "Mujer mayor" to "mujer-mayor",
            "Murim" to "murim",
            "Música" to "musica",
            "Mystery" to "mystery",
            "Nigromante" to "nigromante",
            "No Princeso" to "no-princeso",
            "Novela" to "novela",
            "Novela Ligera" to "novela-ligera",
            "NTR" to "ntr",
            "Nuevo" to "nuevo",
            "OP" to "op",
            "Original" to "original",
            "Otra oportunidad" to "otra-oportunidad",
            "Paladines" to "paladines",
            "Pareja casada" to "pareja-casada",
            "Parodia" to "parodia",
            "Peleas" to "peleas",
            "Poderes sobrenaturales" to "poderes-sobrenaturales",
            "Posible Harem" to "posible-harem",
            "Post-apocalíptico" to "post-apocaliptico",
            "Postapocalíptico" to "postapocaliptico",
            "Prota" to "prota",
            "Prota Badas" to "prota-badas",
            "Prota OP" to "prota-op",
            "Próximamente" to "proximamente",
            "Psicologico" to "psicologico",
            "Puto-Amo" to "puto-amo",
            "Realidad" to "realidad",
            "Realidad Virtual" to "realidad-virtual",
            "Recomendado" to "recomendado",
            "Recuentos de la vida" to "recuentos-de-la-vida",
            "Recuerdo de la vida" to "recuerdo-de-la-vida",
            "Reencarnación" to "reencarnacion",
            "reencarnacion" to "reencarnacion-2",
            "Reencarnado" to "reencarnado",
            "Regresión" to "regresion",
            "Reincarnation" to "reincarnation",
            "Relacion secreta" to "relacion-secreta",
            "Renacimiento" to "renacimiento",
            "Retornado" to "retornado",
            "Retorno" to "retorno",
            "Rey Demonio" to "rey-demonio",
            "Romance" to "romance",
            "Romance? Quien sabe" to "romance-quien-sabe",
            "RPG" to "rpg",
            "Samurai" to "samurai",
            "Sci-fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shonen" to "shonen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Sistema" to "sistema",
            "Sistema de Niveles" to "sistema-de-niveles",
            "Sistemas" to "sistemas",
            "sistemas de trucos" to "sistemas-de-trucos",
            "Slice of Life" to "slice-of-life",
            "Sobrenatural" to "sobrenatural",
            "Super poderes" to "super-poderes",
            "Supernatural" to "supernatural",
            "Superpoderes" to "superpoderes",
            "Supervivencia" to "supervivencia",
            "Suspenso" to "suspenso",
            "Telenovela" to "telenovela",
            "Thriller" to "thriller",
            "Tragedia" to "tragedia",
            "Tragico" to "tragico",
            "Transmigración" to "transmigracion",
            "Transmigración entre mundos" to "transmigracion-entre-mundos",
            "Urbano" to "urbano",
            "Vampiros" to "vampiros",
            "Venganza" to "venganza",
            "viaje en el tiempo" to "viaje-en-el-tiempo",
            "Vida Cotidiana" to "vida-cotidiana",
            "Vida Escolar" to "vida-escolar",
            "video juegos" to "video-juegos",
            "Villano" to "villano",
            "waifus" to "waifus",
            "Web Comic" to "web-comic",
            "Webtoon" to "webtoon",
            "Zombies" to "zombies",
        )
    }
}
