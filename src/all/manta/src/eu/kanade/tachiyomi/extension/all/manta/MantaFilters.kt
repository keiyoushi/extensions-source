package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.model.Filter

class Category(lang: String) :
    Filter.Select<String>(
        if (lang == "es") "Categoría" else "Category",
        getValues(lang).map { it.first }.toTypedArray(),
    ) {
    val selectedValues = getValues(lang)

    companion object {
        fun getValues(lang: String) = if (lang == "es") {
            arrayOf(
                Pair("Nueva", "tagId=288"),
                Pair("Exclusiva", "tagId=289"),
                Pair("Finalizada", "tagId=287"),
                Pair("Romantasía", "tagId=355"),
                Pair("Romance", "tagId=3"),
                Pair("BL", "tagId=16"),
                Pair("Comedia", "tagId=13"),
                Pair("Histórico", "tagId=7"),
                Pair("Vida cotidiana", "tagId=11"),
            )
        } else {
            arrayOf(
                Pair("Unlimited", "tagId=359"),
                Pair("New", "tagId=288"),
                Pair("Exclusive", "tagId=289"),
                Pair("Completed", "tagId=287"),
                Pair("Event", "tagId=354"),
                Pair("Romantasy", "tagId=355"),
                Pair("Romance", "tagId=3"),
                Pair("Fantasy/Sci-Fi", "tagId=14"),
                Pair("Drama", "tagId=2"),
                Pair("Thriller", "tagId=231"),
                Pair("BL", "tagId=16"),
                Pair("GL", "tagId=17"),
                Pair("Nonfiction", "tagId=249"),
                Pair("Action & Adventure", "tagId=361"),
                Pair("Comedy", "tagId=13"),
                Pair("Historical", "tagId=7"),
                Pair("Horror", "tagId=1"),
                Pair("School life", "tagId=74"),
                Pair("Slice of life", "tagId=11"),
                Pair("Sports", "tagId=6"),
                Pair("Suspense & Thriller", "tagId=362"),
            )
        }
    }
}

val List<Filter<*>>.category: Pair<String, String>
    get() = filterIsInstance<Category>().firstOrNull()?.let {
        it.selectedValues[it.state]
    } ?: Pair("", "")
