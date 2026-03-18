package eu.kanade.tachiyomi.extension.en.manta

import eu.kanade.tachiyomi.source.model.Filter

class Category :
    Filter.Select<String>(
        "Category",
        values.map { it.first }.toTypedArray(),
    ) {
    companion object {
        val values = arrayOf(
            Pair("Any", ""),
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
        )
    }
}

val List<Filter<*>>.category: Pair<String, String>
    get() = filterIsInstance<Category>().firstOrNull()?.let {
        Category.values[it.state]
    } ?: Pair("Any", "")
