package eu.kanade.tachiyomi.extension.id.komiknextgonline

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    UriPartFilter(
        "Kategori",
        arrayOf(
            Pair("Semua", ""),
            Pair("Pendidikan", "pendidikan"),
            Pair("Persahabatan", "persahabatan"),
            Pair("Anak Islami", "anak-islami"),
            Pair("Horor dan Misteri", "horor-dan-misteri"),
        ),
    )

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
