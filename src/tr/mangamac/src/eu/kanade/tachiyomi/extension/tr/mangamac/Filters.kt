package eu.kanade.tachiyomi.extension.tr.mangamac

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class TypeFilter : Filter.Select<String>("Tür", arrayOf("Tümü", "Manga", "Webtoon")) {
    fun catalogs() = when (state) {
        1 -> listOf(MANGA_CATALOG)
        2 -> listOf(WEBTOON_CATALOG)
        else -> listOf(MANGA_CATALOG, WEBTOON_CATALOG)
    }
}

private const val MANGA_CATALOG = "mangalar"
private const val WEBTOON_CATALOG = "browse-webtoons"

class GenreFilter :
    UriPartFilter(
        "Türler",
        arrayOf(
            "Tümü" to "",
            "+18" to "22",
            "Aksiyon" to "1",
            "Aliens" to "32",
            "Askeri" to "29",
            "Bilim Kurgu" to "7",
            "Büyü" to "26",
            "Demons" to "35",
            "Drama" to "4",
            "Ecchi" to "28",
            "FanMade" to "37",
            "Fantastik" to "5",
            "Fantezi" to "34",
            "Gizem" to "9",
            "Harem" to "24",
            "Isekai" to "19",
            "Josei" to "18",
            "Komedi" to "3",
            "Korku" to "8",
            "Macera" to "2",
            "Martial Arts" to "36",
            "Monster" to "31",
            "Okul Hayatı" to "21",
            "One Shot" to "25",
            "Psikolojik" to "10",
            "Romantik" to "6",
            "Seinen" to "15",
            "Shoujo" to "17",
            "Shounen" to "16",
            "Slice of Life" to "11",
            "Spor" to "12",
            "Suç" to "27",
            "Supernatural" to "13",
            "Tarihi" to "20",
            "Thriller" to "14",
            "Vampir" to "30",
            "War" to "23",
            "Western" to "33",
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

class SortFilter :
    UriPartFilter(
        "Sırala",
        arrayOf(
            "En Yeni" to "latest",
            "En Popüler" to "popular",
            "En Yüksek Puan" to "rating",
            "Son Güncellenen" to "updated",
            "A-Z" to "title",
        ),
        state = 1,
    )
