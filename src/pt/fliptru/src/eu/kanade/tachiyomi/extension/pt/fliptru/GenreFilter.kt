package eu.kanade.tachiyomi.extension.pt.fliptru

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    Filter.Select<String>(
        "Gênero",
        arrayOf(
            "Todos os gêneros",
            "Ação",
            "Adulto",
            "Aventura",
            "Comédia",
            "Drama",
            "Educação",
            "Esportes",
            "Fantasia",
            "Ficção Científica",
            "LGBTQIA+",
            "Mistério",
            "Não-ficção",
            "Policial",
            "Romance",
            "Slice of Life",
            "Suspense",
            "Terror",
            "Tirinhas",
        ),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "acao"
        2 -> "adulto"
        3 -> "aventura"
        4 -> "comedia"
        5 -> "drama"
        6 -> "educacao"
        7 -> "esportes"
        8 -> "fantasia"
        9 -> "ficcao-cientifica"
        10 -> "lgbtqia"
        11 -> "misterio"
        12 -> "nao-ficcao"
        13 -> "policial"
        14 -> "romance"
        15 -> "slice-life"
        16 -> "suspense"
        17 -> "terror"
        18 -> "tirinhas"
        else -> ""
    }
}
