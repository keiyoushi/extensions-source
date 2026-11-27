package eu.kanade.tachiyomi.extension.tr.piescans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class UriPartFilter(
    displayName: String,
    private val parameter: String,
    private val options: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray(), state) {
    val queryParameter get() = parameter to options[state].second
}

// The site's rating sort, release year filter and novel type return nothing
class SortFilter :
    UriPartFilter(
        "Sırala",
        "sort",
        arrayOf("Tarih" to "date", "Görüntülenme" to "views", "Yorumlar" to "comment"),
        state = 1,
    )

class GenreFilter :
    UriPartFilter(
        "Tür",
        "genre",
        arrayOf(
            "Tüm Türler" to "",
            "Aksiyon" to "91",
            "Bilim Kurgu" to "113",
            "Doğaüstü" to "100",
            "Dövüş Sanatları" to "92",
            "Dram" to "166",
            "Drama" to "137",
            "Ecchi" to "72",
            "Fantastik" to "93",
            "Fantezi" to "103",
            "Gizem" to "110",
            "Harem" to "38",
            "Isekai" to "167",
            "Josei" to "75",
            "Komedi" to "88",
            "Korku" to "111",
            "Macera" to "112",
            "Manga" to "171",
            "Mature" to "106",
            "Mecha" to "56",
            "Novel" to "127",
            "Okul Hayatı" to "89",
            "Olgun" to "162",
            "Psikolojik" to "97",
            "Reenkarnasyon" to "181",
            "Romance" to "225",
            "Romantizm" to "101",
            "Seinen" to "19",
            "Shoujo" to "30",
            "Shounen" to "23",
            "Spor" to "227",
            "Tarihi" to "94",
            "Tarihsel" to "135",
            "Trajedi" to "134",
            "Villainess" to "168",
            "Yaşamdan Alıntı" to "131",
            "Yetişkin" to "107",
            "Yuri" to "174",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tip",
        "type",
        arrayOf("Tümü" to "", "Manga" to "manga", "Webtoon" to "webtoon", "Manhwa" to "manhwa", "Manhua" to "manhua"),
    )

class StatusFilter :
    UriPartFilter(
        "Durum",
        "manga_status",
        arrayOf(
            "Tümü" to "",
            "Devam Ediyor" to "publishing",
            "Final" to "finished",
            "Askıda" to "on-hiatus",
            "Bırakıldı" to "discontinued",
            "Henüz Yayınlanmadı" to "not-yet-published",
        ),
    )

fun getFilters() = FilterList(
    SortFilter(),
    GenreFilter(),
    TypeFilter(),
    StatusFilter(),
)
