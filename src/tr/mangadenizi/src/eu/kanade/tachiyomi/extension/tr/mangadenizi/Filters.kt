package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class SortFilter(state: Int = 0) :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            Pair("Varsayılan", ""),
            Pair("En Popüler", "popular"),
            Pair("Son Güncellenen", "latest"),
            Pair("En Yüksek Puan", "rating"),
            Pair("Alfabetik (A-Z)", "name"),
            Pair("Yıl (Yeni-Eski)", "year"),
        ),
        state,
    )

class StatusFilter(state: Int = 0) :
    UriPartFilter(
        "Durum",
        arrayOf(
            Pair("Tümü", ""),
            Pair("Devam Ediyor", "ongoing"),
            Pair("Tamamlandı", "completed"),
            Pair("Ara Verildi", "hiatus"),
            Pair("İptal Edildi", "cancelled"),
        ),
        state,
    )

class CategoryFilter(state: Int = 0) :
    UriPartFilter(
        "Kategori",
        arrayOf(
            Pair("Tümü", ""),
            Pair("Aksiyon", "aksiyon"),
            Pair("Bilim Kurgu", "bilim-kurgu"),
            Pair("Doğaüstü", "dogaustu"),
            Pair("Dövüş Sanatları", "dovus-sanatlari"),
            Pair("Dram", "dram"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantastik", "fantastik"),
            Pair("Gerilim", "gerilim"),
            Pair("Gizem", "gizem"),
            Pair("Hayattan Bir Parça", "hayattan-bir-parca"),
            Pair("Hayattan Kesitler", "hayattan-kesitler"),
            Pair("Komedi", "komedi"),
            Pair("Korku", "korku"),
            Pair("Macera", "macera"),
            Pair("Psikolojik", "psikolojik"),
            Pair("Romantizm", "romantizm"),
            Pair("Spor", "spor"),
            Pair("Tarihi", "tarihi"),
            Pair("Trajedi", "trajedi"),
        ),
        state,
    )

class DemographicFilter(state: Int = 0) :
    UriPartFilter(
        "Demografi",
        arrayOf(
            Pair("Tümü", ""),
            Pair("Genç Erkek (Shounen)", "1"),
            Pair("Genç Kız (Shoujo)", "2"),
            Pair("Yetişkin Erkek (Seinen)", "3"),
            Pair("Yetişkin Kadın (Josei)", "4"),
            Pair("Çocuk (Kodomo)", "5"),
        ),
        state,
    )
