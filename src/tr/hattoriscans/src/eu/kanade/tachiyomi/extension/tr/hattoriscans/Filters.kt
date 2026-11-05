package eu.kanade.tachiyomi.extension.tr.hattoriscans

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val selected get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sıralama",
        arrayOf(
            "Son Eklenen" to "newest",
            "En Yüksek Puan" to "rating",
            "İlk Eklenen" to "oldest",
            "A → Z" to "az",
            "Z → A" to "za",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tür",
        arrayOf(
            "Tümü" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Durum",
        arrayOf(
            "Tümü" to "",
            "Devam Ediyor" to "ongoing",
            "Güncel" to "current",
            "Tamamlandı" to "completed",
            "Ara Verildi" to "hiatus",
            "İptal Edildi" to "cancelled",
        ),
    )

class GenreModeFilter :
    SelectFilter(
        "Kategori eşleşmesi",
        arrayOf(
            "Hepsi" to "all",
            "Herhangi biri" to "any",
        ),
    )

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) : Filter.Group<Genre>("Kategoriler", genres.map(::Genre)) {
    val selected get() = state.filter { it.state }.map { it.name }
}
