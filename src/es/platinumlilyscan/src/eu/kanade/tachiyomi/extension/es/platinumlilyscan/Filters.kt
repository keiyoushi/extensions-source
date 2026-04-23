package eu.kanade.tachiyomi.extension.es.platinumlilyscan

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter :
    Filter.Select<String>(
        "Tipo",
        arrayOf(
            "Todos",
            "Manga",
            "Manhwa",
            "Manhua",
            "Doujinshi",
            "One-Shot",
        ),
    )

class StatusFilter :
    Filter.Select<String>(
        "Estado",
        arrayOf(
            "Todos",
            "Publicándose",
            "Finalizado",
            "Hiatus",
        ),
    )

class ContentRatingFilter :
    Filter.Select<String>(
        "Clasificación de contenido",
        arrayOf(
            "Todos",
            "Seguro",
            "Sugestivo",
            "NSFW",
        ),
    )

class GenreFilter :
    Filter.Select<String>(
        "Género",
        arrayOf(
            "Todos",
            "Acción",
            "Apocalíptico",
            "Aventura",
            "Ciencia Ficción",
            "Cocina",
            "Comedia",
            "Drama",
            "Ecchi",
            "Escolar",
            "Fantasía",
            "Histórico",
            "Horror",
            "Isekai",
            "Magia",
            "Mecha",
            "Misterio",
            "Música",
            "Psicológico",
            "Romance",
            "Slice of Life",
            "Sobrenatural",
            "Supervivencia",
            "Tragedia",
            "Vampiros",
            "Yuri",
        ),
    )
