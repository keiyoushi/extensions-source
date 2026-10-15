package eu.kanade.tachiyomi.extension.tr.mangaportali

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            "En Yeni" to "new",
            "Son Güncellenen" to "updated",
            "Trend" to "trending",
            "Popüler" to "popular",
            "Puan" to "rating",
            "A-Z" to "alpha",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tür",
        arrayOf(
            "Hepsi" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Durum",
        arrayOf(
            "Hepsi" to "",
            "Devam Ediyor" to "ONGOING",
            "Tamamlandı" to "COMPLETED",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Kategori",
        arrayOf(
            "Hepsi" to "",
            "Adaptasyon" to "adaptasyon",
            "Aile" to "aile",
            "Akademi" to "akademi",
            "Aksiyon" to "aksiyon",
            "Baba & Cocuk" to "baba-cocuk",
            "Bilim Kurgu" to "bilim-kurgu",
            "Buyu" to "buyu",
            "Canavar" to "canavar",
            "Cok Guclu" to "cok-guclu",
            "Dahi" to "dahi",
            "Deha" to "deha",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Eglence" to "eglence",
            "Fantastik" to "fantastik",
            "Fantazi" to "fantazi",
            "Geri Donen" to "geri-donen",
            "Gerileme" to "gerileme",
            "Gerilim" to "gerilim",
            "Gizem" to "gizem",
            "Gunluk Hayat" to "gunluk-hayat",
            "Harem" to "harem",
            "Hayatta Kalma" to "hayatta-kalma",
            "Hayattan Kesitler" to "hayattan-kesitler",
            "Hayvanlar" to "hayvanlar",
            "Intikam" to "intikam",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Karanlik" to "karanlik",
            "Komedi" to "komedi",
            "Korku" to "korku",
            "Kule" to "kule",
            "Macera" to "macera",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Meka" to "meka",
            "Modern" to "modern",
            "Modern Hayat" to "modern-hayat",
            "Murim" to "murim",
            "Muzik" to "muzik",
            "Okul" to "okul",
            "Okul Hayati" to "okul-hayati",
            "Psikoloji" to "psikoloji",
            "Psikolojik" to "psikolojik",
            "Reenkarnasyon" to "reenkarnasyon",
            "Romantik" to "romantik",
            "Romantizm" to "romantizm",
            "Sanal Gerceklik" to "sanal-gerceklik",
            "Seinen" to "seinen",
            "Seytan" to "seytan",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Sihir" to "sihir",
            "Sistem" to "sistem",
            "Superkahraman" to "superkahraman",
            "Supernatural" to "supernatural",
            "Tarihi" to "tarihi",
            "Trajedi" to "trajedi",
            "Vampir" to "vampir",
            "Video Oyunlari" to "video-oyunlari",
            "Villain" to "villain",
            "Yandere" to "yandere",
            "Zindan" to "zindan",
        ),
    )

class TagFilter :
    UriPartFilter(
        "Etiket",
        arrayOf(
            "Hepsi" to "",
            "Aksiyon" to "aksiyon",
            "Asiri Guclu" to "asiri-guclu",
            "Avci" to "avci",
            "Buyu" to "buyu",
            "Buyucu" to "buyucu",
            "Buyulu" to "buyulu",
            "Canavar" to "canavar",
            "Dahi Mc" to "dahi-mc",
            "Dogaustu" to "dogaustu",
            "Dovus Sanatlari" to "dovus-sanatlari",
            "Fantastik" to "fantastik",
            "Fantazi" to "fantazi",
            "Gunluk Hayat" to "gunluk-hayat",
            "Harem" to "harem",
            "Isekai" to "isekai",
            "Komedi" to "komedi",
            "Korku" to "korku",
            "Manga" to "manga",
            "Manga Turkce" to "manga-turkce",
            "Manhwa" to "manhwa",
            "Manhwa Turkce" to "manhwa-turkce",
            "Okul Hayati" to "okul-hayati",
            "Psikolojik" to "psikolojik",
            "Reenkarnasyon" to "reenkarnasyon",
            "Romantik" to "romantik",
            "Seviye Atlama" to "seviye-atlama",
            "Sistem" to "sistem",
            "Supernatural" to "supernatural",
            "Trajedi" to "trajedi",
            "Zindan" to "zindan",
            "Zombi" to "zombi",
        ),
    )
