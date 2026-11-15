package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter(state: Int = 0) :
    Filter.Select<String>(
        "Sıralama",
        arrayOf("Varsayılan", "En Yeni", "Popüler", "Puan", "A-Z", "En Eski"),
        state,
    ) {
    fun toUriPart() = arrayOf("default", "updatedAt", "popular", "rating", "name", "createdAt")[state]

    companion object {
        val POPULAR = FilterList(SortFilter(2))
        val LATEST = FilterList(SortFilter(1))
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Durum",
        arrayOf("Tümü", "Devam Ediyor", "Tamamlandı", "Ara Verildi", "Bırakıldı"),
    ) {
    fun toUriPart() = arrayOf("", "ONGOING", "COMPLETED", "HIATUS", "DROPPED")[state]
}

class TypeFilter :
    Filter.Select<String>(
        "Format",
        arrayOf("Tümü", "Manhwa", "Manhua", "Manga"),
    ) {
    fun toUriPart() = arrayOf("", "MANHWA", "MANHUA", "MANGA")[state]
}

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Tür",
        (listOf("Tümü") + genres.map { it.first }).toTypedArray(),
    ) {
    private val uriParts = listOf("") + genres.map { it.second }

    fun toUriPart() = uriParts[state]
}

class MinChaptersFilter :
    Filter.Text(
        "Minimum Bölüm",
        "",
    )
