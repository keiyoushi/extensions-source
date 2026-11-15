package eu.kanade.tachiyomi.extension.tr.mangawt

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            "En Popüler" to "popular",
            "Son Güncelleme" to "updated",
            "Yeni Eklenen" to "newest",
            "En Yüksek Puan" to "rated",
            "En Çok Görüntülenen" to "views",
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

// The API matches type case-sensitively against the stored capitalized values.
class TypeFilter :
    UriPartFilter(
        "Tür",
        arrayOf(
            "Tümü" to "",
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
            "Webtoon" to "Webtoon",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Kategori",
        arrayOf(
            "Tümü" to "",
            "Aksiyon" to "Action",
            "Macera" to "Adventure",
            "Komedi" to "Comedy",
            "Şeytan" to "Demon",
            "Drama" to "Drama",
            "Ecchi" to "Ecchi",
            "Fantastik" to "Fantasy",
            "Harem" to "Harem",
            "Tarihi" to "Historical",
            "Korku" to "Horror",
            "Isekai" to "Isekai",
            "Dövüş Sanatları" to "Martial Arts",
            "Yetişkin" to "Mature",
            "Reenkarnasyon" to "Reincarnation",
            "Romantik" to "Romance",
            "Okul Hayatı" to "School Life",
            "Bilim Kurgu" to "Sci-Fi",
            "Seinen" to "Seinen",
            "Shounen" to "Shounen",
            "Doğaüstü" to "Supernatural",
            "Trajedi" to "Tragedy",
        ),
    )
