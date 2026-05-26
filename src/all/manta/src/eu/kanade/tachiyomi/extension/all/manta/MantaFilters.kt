package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.model.Filter

class Category(lang: String) :
    Filter.Select<String>(
        if (lang == "es") "Categoría" else "Category",
        getValues(lang).map { it.first }.toTypedArray(),
    ) {
    companion object {
        fun getValues(lang: String) = arrayOf(
            Pair(if (lang == "es") "Cualquiera" else "Any", ""),
            Pair("Unlimited", "tagId=359"),
            Pair(if (lang == "es") "Nueva" else "New", "tagId=288"),
            Pair(if (lang == "es") "Exclusiva" else "Exclusive", "tagId=289"),
            Pair(if (lang == "es") "Finalizada" else "Completed", "tagId=287"),
            Pair(if (lang == "es") "Evento" else "Event", "tagId=354"),
            Pair(if (lang == "es") "Romantasía" else "Romantasy", "tagId=355"),
            Pair(if (lang == "es") "Romance" else "Romance", "tagId=3"),
            Pair(if (lang == "es") "Fantasía/Ciencia ficción" else "Fantasy/Sci-Fi", "tagId=14"),
            Pair(if (lang == "es") "Drama" else "Drama", "tagId=2"),
            Pair(if (lang == "es") "Suspense" else "Thriller", "tagId=231"),
            Pair("BL", "tagId=16"),
            Pair("GL", "tagId=17"),
            Pair(if (lang == "es") "No ficción" else "Nonfiction", "tagId=249"),
        )
    }
}

val List<Filter<*>>.category: Pair<String, String>
    get() = filterIsInstance<Category>().firstOrNull()?.let {
        Category.getValues("en")[it.state]
    } ?: Pair("Any", "")
