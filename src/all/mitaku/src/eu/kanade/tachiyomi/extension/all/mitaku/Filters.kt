package eu.kanade.tachiyomi.extension.all.mitaku

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val valuePairs: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, valuePairs.map { it.first }.toTypedArray()) {

    val selected: String?
        get() = valuePairs[state].second.takeIf { it.isNotEmpty() }
}

class CategoryFilter :
    UriPartFilter(
        "Category",
        arrayOf(
            "Any" to "",
            "Ero Cosplay" to "ero-cosplay",
            "Nude" to "nude",
            "Sexy Set" to "sexy-set",
            "Online Video" to "online-video",
        ),
    )

class TagFilter : Filter.Text("Tag") {
    fun toUriPart(): String = state.trim()
        .lowercase()
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString("-")
}
