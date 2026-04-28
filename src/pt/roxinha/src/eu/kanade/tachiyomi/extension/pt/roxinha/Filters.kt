package eu.kanade.tachiyomi.extension.pt.roxinha

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        arrayOf("Título", "Atualização", "Visualizações", "Avaliação"),
        Selection(2, false),
    )

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Todos", "Em andamento", "Concluído"),
    )

class TypeFilter :
    Filter.Select<String>(
        "Tipo",
        arrayOf("Todos", "Manga", "Manhua", "Manhwa", "Webtoon"),
    )
