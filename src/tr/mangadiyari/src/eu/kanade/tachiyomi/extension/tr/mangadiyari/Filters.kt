package eu.kanade.tachiyomi.extension.tr.mangadiyari

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            "Son Güncellenen" to "latest",
            "En Popüler" to "popular",
            "En Yüksek Puan" to "rating",
            "A-Z" to "title",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tür",
        arrayOf(
            "Tümü" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Çizgi Roman" to "comic",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Durum",
        arrayOf(
            "Tümü" to "",
            "Devam Ediyor" to "ongoing",
            "Tamamlandı" to "completed",
            "Ara Verildi" to "hiatus",
        ),
    )

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<GenreCheckBox>(
        "Kategoriler",
        listOf(
            "Aksiyon" to "Action",
            "Macera" to "Adventure",
            "Komedi" to "Comedy",
            "Drama" to "Drama",
            "Fantastik" to "Fantasy",
            "Tarihi" to "Historical",
            "Korku" to "Horror",
            "Isekai" to "Isekai",
            "Dövüş Sanatları" to "Martial Arts",
            "Gizem" to "Mystery",
            "Reenkarnasyon" to "Reincarnation",
            "Romantik" to "Romance",
            "Okul" to "School",
            "Bilim Kurgu" to "Sci-Fi",
            "Doğaüstü" to "Supernatural",
            "Gerilim" to "Thriller",
            "Ecchi" to "Ecchi",
            "Harem" to "Harem",
            "Josei" to "Josei",
            "Yetişkin" to "Mature",
            "Mecha" to "Mecha",
            "Psikolojik" to "Psychological",
            "Seinen" to "Seinen",
            "Shoujo" to "Shoujo",
            "Shounen" to "Shounen",
            "Günlük Yaşam" to "Slice of Life",
            "Spor" to "Sports",
            "Trajedi" to "Tragedy",
            "Webtoon" to "Webtoon",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ).map { GenreCheckBox(it.first, it.second) },
    ) {
    fun toUriPart(): String = state.filter { it.state }.joinToString(",") { it.value }
}
