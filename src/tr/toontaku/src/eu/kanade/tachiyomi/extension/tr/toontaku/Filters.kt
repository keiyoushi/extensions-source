package eu.kanade.tachiyomi.extension.tr.toontaku

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second.ifEmpty { null }
}

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            "Popülerlik" to "totalViews,desc",
            "Yeni eklenen" to "createdAt,desc",
            "A-Z" to "title,asc",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tür",
        arrayOf(
            "Tümü" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Durum",
        arrayOf(
            "Tümü" to "",
            "Devam Ediyor" to "DEVAM_EDIYOR",
            "Tamamlandı" to "TAMAMLANDI",
            "Durakladı" to "DURAKLADI",
            "Bırakıldı" to "BIRAKILDI",
        ),
    )

class OnlyFreeFilter : Filter.CheckBox("Sadece ücretsiz seriler")

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<FilterGenreDto>) :
    Filter.Group<GenreCheckBox>(
        "Kategoriler",
        genres.map { GenreCheckBox(it.name, it.slug) },
    ) {
    fun toUriPart(): String? = state.filter { it.state }.joinToString(",") { it.slug }.ifEmpty { null }
}
