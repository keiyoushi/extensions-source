package eu.kanade.tachiyomi.extension.pt.fenixproject

import eu.kanade.tachiyomi.source.model.Filter

class Filters : Filter.Select<String>("Gênero", genres.map { it.first }.toTypedArray()) {
    val genreId: String? get() = genres[state].second.ifBlank { null }
}

private val genres = listOf(
    "Todos" to "",
    "Adaptação" to "12",
    "Amigos de Infância" to "14",
    "Angústia" to "15",
    "Animais" to "51",
    "Aventura" to "2",
    "Ação" to "1",
    "Casamento Arranjado" to "17",
    "Comédia" to "3",
    "Conto" to "18",
    "Dark Romance" to "9",
    "Demônios" to "50",
    "Drama" to "4",
    "Escolar" to "19",
    "Escritório" to "20",
    "Familia" to "48",
    "Fantasia" to "5",
    "Ficção Científica" to "49",
    "Harém Reverso" to "24",
    "Histórico" to "11",
    "Josei" to "25",
    "Magia" to "26",
    "Mistério" to "6",
    "Moderno" to "10",
    "Médico" to "38",
    "Oneshot" to "29",
    "Redenção" to "30",
    "Reencarnação" to "31",
    "Romance" to "7",
    "Shoujo" to "33",
    "Shounen" to "34",
    "Slice of Life" to "35",
    "Sobrenatural" to "36",
    "Stalker" to "37",
    "Terror" to "8",
    "Tragédia" to "39",
    "Traição" to "40",
    "Transmigração" to "41",
    "Troca de Corpos" to "42",
    "Vampiro" to "43",
    "Viagem no Tempo" to "44",
    "Vingança" to "47",
    "Yuri" to "46",
)
