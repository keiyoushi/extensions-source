package eu.kanade.tachiyomi.extension.tr.okutoon

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            Pair("En Yeni", "newest"),
            Pair("Son Güncellenenler", "updated"),
            Pair("En Popüler", "popular"),
            Pair("En Yüksek Puan", "rating"),
            Pair("A-Z", "az"),
        ),
    ) {
    init {
        state = 1 // Defaults to 'Son Güncellenenler' exactly like the website
    }
}

class StatusFilter :
    UriPartFilter(
        "Durum",
        arrayOf(
            Pair("Tümü", ""),
            Pair("Devam Ediyor", "ongoing"),
            Pair("Tamamlandı", "completed"),
            Pair("Ara Verildi", "hiatus"),
        ),
    )

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Türler", genres)

fun getGenreList() = listOf(
    Genre("Aksiyon", "aksiyon"),
    Genre("Askeri", "askeri"),
    Genre("Bilim Kurgu", "bilim-kurgu"),
    Genre("Doğaüstü", "dogaustu"),
    Genre("Dövüş Sanatları", "dovus-sanatlari"),
    Genre("Dram", "dram"),
    Genre("Fantezi", "fantezi"),
    Genre("Gerilim", "gerilim"),
    Genre("Gizem", "gizem"),
    Genre("Günlük Hayat", "gunluk-hayat"),
    Genre("Harem", "harem"),
    Genre("Hayatta Kalma", "hayatta-kalma"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Kıyamet", "kiyamet"),
    Genre("Komedi", "komedi"),
    Genre("Korku", "korku"),
    Genre("Macera", "macera"),
    Genre("Okul Hayatı", "okul-hayati"),
    Genre("Oyun", "oyun"),
    Genre("Psikolojik", "psikolojik"),
    Genre("Reenkarnasyon", "reenkarnasyon"),
    Genre("Romantizm", "romantizm"),
    Genre("Shoujo", "shoujo"),
    Genre("Shounen", "shounen"),
    Genre("Sihir", "sihir"),
    Genre("Suç", "suc"),
    Genre("Süperkahraman", "superkahraman"),
    Genre("Tarihi", "tarihi"),
    Genre("Yaoi", "yaoi"),
)
