package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sıralama",
        arrayOf("Varsayılan", "En Yeni", "Popüler", "Puan", "A-Z", "En Eski"),
    ) {
    fun toUriPart() = arrayOf("default", "updatedAt", "popular", "rating", "name", "createdAt")[state]
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

class GenreFilter :
    Filter.Select<String>(
        "Tür",
        arrayOf(
            "Tümü",
            "Aksiyon",
            "Fantastik",
            "Harem",
            "Komedi",
            "Macera",
            "Bilim Kurgu",
            "Dövüş Sanatları",
            "Shounen",
            "Doğaüstü",
            "Dram",
            "Romantizm",
            "Seinen",
            "Sistem",
            "Tarihsel",
            "Aşırı Güçlü",
            "İkinci Şans",
            "İntikam",
            "Murim",
            "Yeniden Doğuş",
            "Gizli Kimlik",
            "Okul",
            "Süper Güçler",
            "Süper Kahramanlar",
            "Okul Hayatı",
            "Komplo",
            "Gerilim",
            "Karanlık Fantezi",
            "Korku",
            "Manhwa",
            "Büyü",
            "Genç",
            "Modern",
            "Gerileme",
            "Yaşamdan Kesitler",
            "Dahi Mc",
            "Şiddet",
            "Drama",
            "Fantezi",
            "Olgun",
            "Psikolojik",
            "Romantik",
            "Fantazi",
            "Canavar",
            "Dedektif",
            "Gizem",
            "Kıyamet",
            "Lise",
            "Ecchi",
            "Manga",
            "İsekai",
            "Reenkarnasyon",
            "Supernatural",
            "Dövüş",
            "Trajedi",
            "Oyun",
            "Sanal Gerçeklik",
            "makine",
        ),
    ) {
    fun toUriPart() = arrayOf(
        "",
        "Aksiyon",
        "Fantastik",
        "Harem",
        "Komedi",
        "Macera",
        "Bilim Kurgu",
        "Dövüş Sanatları",
        "Shounen",
        "Doğaüstü",
        "Dram",
        "Romantizm",
        "Seinen",
        "Sistem",
        "Tarihsel",
        "Aşırı Güçlü",
        "İkinci Şans",
        "İntikam",
        "Murim",
        "Yeniden Doğuş",
        "Gizli Kimlik",
        "Okul",
        "Süper Güçler",
        "Süper Kahramanlar",
        "Okul Hayatı",
        "Komplo",
        "Gerilim",
        "Karanlık Fantezi",
        "Korku",
        "Manhwa",
        "Büyü",
        "Genç",
        "Modern",
        "Gerileme",
        "Yaşamdan Kesitler",
        "Dahi Mc",
        "Şiddet",
        "Drama",
        "Fantezi",
        "Olgun",
        "Psikolojik",
        "Romantik",
        "Fantazi",
        "Canavar",
        "Dedektif",
        "Gizem",
        "Kıyamet",
        "Lise",
        "Ecchi",
        "Manga",
        "İsekai",
        "Reenkarnasyon",
        "Supernatural",
        "Dövüş",
        "Trajedi",
        "Oyun",
        "Sanal Gerçeklik",
        "makine",
    )[state]
}

class MinChaptersFilter :
    Filter.Text(
        "Minimum Bölüm",
        "",
    )
