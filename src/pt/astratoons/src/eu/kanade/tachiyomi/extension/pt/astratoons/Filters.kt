package eu.kanade.tachiyomi.extension.pt.astratoons

import eu.kanade.tachiyomi.source.model.Filter

internal class CheckBox(name: String, val value: String) : Filter.CheckBox(name)

internal class SortFilter : Filter.Select<String>("Ordenar por", arrayOf("Mais Recente", "Título", "Nº de Capítulos")) {
    fun toQuery() = when (state) {
        0 -> "updated_at"
        1 -> "title"
        2 -> "chapters_count"
        else -> "updated_at"
    }
}

internal class StatusFilter : Filter.Select<String>("Status", arrayOf("Todo Status", "Em dia", "Em andamento", "Completo", "Cancelado", "Hiato", "Dropado")) {
    fun toQuery() = when (state) {
        1 -> "em dia"
        2 -> "em andamento"
        3 -> "completo"
        4 -> "cancelado"
        5 -> "hiato"
        6 -> "dropado"
        else -> ""
    }
}

internal class TypeFilter :
    Filter.Group<CheckBox>(
        "Tipos",
        listOf(
            CheckBox("Manga", "Manga"),
            CheckBox("Manhwa", "Manhwa"),
            CheckBox("Manhua", "Manhua"),
            CheckBox("Webtoon", "Webtoon"),
        ),
    )

internal class TagFilter :
    Filter.Group<CheckBox>(
        "Gêneros",
        listOf(
            CheckBox("Ação", "acao"),
            CheckBox("Artes Marciais", "artes-marciais"),
            CheckBox("Aventura", "aventura"),
            CheckBox("Comédia", "comedia"),
            CheckBox("Crime", "crime"),
            CheckBox("Cultivo", "cultivo"),
            CheckBox("Dança", "danca"),
            CheckBox("Drama", "drama"),
            CheckBox("Ecchi", "ecchi"),
            CheckBox("Esporte", "esporte"),
            CheckBox("Fantasia", "fantasia"),
            CheckBox("Harém", "harem"),
            CheckBox("Histórico", "historico"),
            CheckBox("Isekai", "isekai"),
            CheckBox("Iskai", "iskai"),
            CheckBox("Magia", "magia"),
            CheckBox("Mistério", "misterio"),
            CheckBox("Monstros", "monstros"),
            CheckBox("Murim", "murim"),
            CheckBox("Reencarnação", "reencarnacao"),
            CheckBox("Regressão", "regressao"),
            CheckBox("Romance", "romance"),
            CheckBox("Sci-fi", "sci-fi"),
            CheckBox("Seinen", "seinen"),
            CheckBox("Shounen", "shounen"),
            CheckBox("Silence", "silence"),
            CheckBox("Slice of Life", "slice-of-life"),
            CheckBox("Sobrenatural", "sobrenatural"),
            CheckBox("Super poderes", "super-poderes"),
            CheckBox("Suspense", "suspense"),
            CheckBox("Tragédia", "tragedia"),
            CheckBox("Troca de gênero", "troca-de-genero"),
            CheckBox("Viagem no tempo", "viagem-no-tempo"),
            CheckBox("Vida Cotidiana", "vida-cotidiana"),
            CheckBox("Vida Escolar", "vida-escolar"),
            CheckBox("Zumbis", "zumbis"),
        ),
    )
