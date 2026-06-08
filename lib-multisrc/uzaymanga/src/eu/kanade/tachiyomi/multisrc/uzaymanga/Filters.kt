package eu.kanade.tachiyomi.multisrc.uzaymanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

open class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            Pair("En Yeni", "new"),
            Pair("En Popüler", "popular"),
            Pair("A-Z", "name"),
            Pair("Son Güncelleme", "update"),
        ),
    )

open class StatusFilter :
    UriPartFilter(
        "Durum",
        arrayOf(
            Pair("Seçiniz...", ""),
            Pair("Devam Ediyor", "1"),
            Pair("Tamamlandı", "2"),
            Pair("Durduruldu", "3"),
        ),
    )

open class CountryFilter :
    UriPartFilter(
        "Ülke",
        arrayOf(
            Pair("Seçiniz...", ""),
            Pair("Japonya", "1"),
            Pair("Güney Kore", "2"),
            Pair("Çin", "3"),
            Pair("Diğer", "4"),
        ),
    )

open class CategoryFilter :
    UriPartFilter(
        "Kategori",
        arrayOf(
            Pair("Seçiniz...", ""),
            Pair("Aksiyon", "aksiyon"),
            Pair("Avcı", "avci"),
            Pair("Bebek", "bebek"),
            Pair("Büyü", "buyu"),
            Pair("Canavar", "canavar"),
            Pair("Çete", "cete"),
            Pair("Cin", "cin"),
            Pair("Cin-serisi", "cin-serisi"),
            Pair("Doğaüstü", "dogaustu"),
            Pair("Doktor", "doktor"),
            Pair("Dövüş", "dovus"),
            Pair("Dövüş-sanatları", "dovus-sanatlari"),
            Pair("Dram", "dram"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantastik", "fantastik"),
            Pair("Fantezi", "fantezi"),
            Pair("Geçmişe-dönme", "gecmise-donme"),
            Pair("Geri-dönüş", "geri-donus"),
            Pair("Gizem", "gizem"),
            Pair("Harem", "harem"),
            Pair("Hayattan-kesitler", "hayattan-kesitler"),
            Pair("Intikam", "intikam"),
            Pair("Isekai", "isekai"),
            Pair("Komedi", "komedi"),
            Pair("Kule", "kule"),
            Pair("Macera", "macera"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Mature", "mature"),
            Pair("Murim", "murim"),
            Pair("Okul", "okul"),
            Pair("Okul-hayatı", "okul-hayati"),
            Pair("Oyun", "oyun"),
            Pair("Peri", "peri"),
            Pair("Reankarnasyon", "reankarnasyon"),
            Pair("Reankarne", "reankarne"),
            Pair("Romantik", "romantik"),
            Pair("Romantizm", "romantizm"),
            Pair("Sanal-gerçeklik", "sanal-gerceklik"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Şeytani", "seytani"),
            Pair("Shounen", "shounen"),
            Pair("Şiddet", "siddet"),
            Pair("Sistem", "sistem"),
            Pair("Spor", "spor"),
            Pair("Super-güç", "super-guc"),
            Pair("Tarihi", "tarihi"),
            Pair("Trajedi", "trajedi"),
            Pair("Uzay Manga", "uzay-manga"),
            Pair("Webtoon", "webtoon"),
            Pair("Yetişkin", "yetiskin"),
            Pair("Yıldız", "yildiz"),
            Pair("Zindan", "zindan"),
            Pair("Zindanlar", "zindanlar"),
            Pair("Zorbalar", "zorbalar"),
        ),
    )
