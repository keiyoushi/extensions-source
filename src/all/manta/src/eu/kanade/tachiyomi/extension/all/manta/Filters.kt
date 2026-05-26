package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.model.Filter

private class LangText(val en: String, val es: String)

private val categories = listOf(
    LangText("Any", "Cualquiera") to "",
    LangText("Unlimited", "Unlimited") to "tagId=359",
    LangText("New", "Nuevo") to "tagId=288",
    LangText("Exclusive", "Exclusiva") to "tagId=289",
    LangText("Completed", "Terminado") to "tagId=287",
    LangText("Event", "Evento") to "tagId=354",
    LangText("Romantasy", "Romantasy") to "tagId=355",
    LangText("Romance", "Romance") to "tagId=3",
    LangText("Fantasy/Sci-Fi", "Fantasía/Ciencia Ficción") to "tagId=14",
    LangText("Drama", "Drama") to "tagId=2",
    LangText("Thriller", "Suspenso") to "tagId=231",
    LangText("BL", "BL") to "tagId=16",
    LangText("GL", "GL") to "tagId=17",
    LangText("Nonfiction", "No ficción") to "tagId=249",
)

class Category(lang: String) :
    Filter.Select<String>(
        if (lang == "es") "Categoría" else "Category",
        categories.map { if (lang == "es") it.first.es else it.first.en }.toTypedArray(),
    ) {
    val second get() = categories[state].second
}

class GenreFilter(lang: String) :
    Filter.Select<String>(
        if (lang == "es") "Género" else "Genre",
        arrayOf(if (lang == "es") "Todos" else "All"),
    )
