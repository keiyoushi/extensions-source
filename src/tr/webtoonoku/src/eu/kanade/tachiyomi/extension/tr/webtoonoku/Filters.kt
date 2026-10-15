package eu.kanade.tachiyomi.extension.tr.webtoonoku

import eu.kanade.tachiyomi.source.model.Filter

class UriPartCheckBox(name: String, val value: String) : Filter.CheckBox(name)

abstract class UriPartGroup(name: String, val param: String, options: List<Pair<String, String>>) : Filter.Group<UriPartCheckBox>(name, options.map { UriPartCheckBox(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.value }
}

class OrderFilter : Filter.Select<String>("Sıralama", options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second

    companion object {
        private val options = listOf(
            "Popüler" to "popular",
            "En Yeniler" to "latest",
            "Puan" to "rating",
            "A-Z" to "title",
            "Z-A" to "title-desc",
        )
    }
}

class StatusFilter :
    UriPartGroup(
        "Durum",
        "status[]",
        listOf(
            "Devam Ediyor" to "Ongoing",
            "Tamamlandı" to "Completed",
            "Ara Verildi" to "Hiatus",
            "Bırakıldı" to "Dropped",
        ),
    )

class TypeFilter :
    UriPartGroup(
        "Tip",
        "type[]",
        listOf(
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
        ),
    )

class GenreFilter :
    UriPartGroup(
        "Türler",
        "genre[]",
        listOf(
            "Adaptasyon" to "adaptasyon",
            "Aksiyon" to "aksiyon",
            "Aşçılık" to "ascilik",
            "Bilim Kurgu" to "bilim-kurgu",
            "Doğaüstü" to "dogaustu",
            "Dövüş Sanatları" to "dovus-sanatlari",
            "Dram" to "dram",
            "Drama" to "drama",
            "Fantastik" to "fantastik",
            "Gerilim" to "gerilim",
            "Gizem" to "gizem",
            "Hayatta Kalma" to "hayatta-kalma",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Kesitler" to "kesitler",
            "Kıyamet" to "kiyamet",
            "Komedi" to "komedi",
            "Korku" to "korku",
            "Macera" to "macera",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Manhwa" to "manhwa",
            "Mecha" to "mecha",
            "Medikal" to "medikal",
            "Okul" to "okul",
            "Okul Hayatı" to "okul-hayati",
            "Oyun" to "oyun",
            "Psikolojik" to "psikolojik",
            "Reenkarnasyon" to "reenkarnasyon",
            "Reenkarne" to "reenkarne",
            "Romantik" to "romantik",
            "Romantizm" to "romantizm",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Sistem" to "sistem",
            "Slice of Life" to "slice-of-life",
            "Spor" to "spor",
            "Tarihi" to "tarihi",
            "Trajedi" to "trajedi",
            "Webtoon" to "webtoon",
            "Yapay Zeka Çeviri" to "yapay-zeka-ceviri",
        ),
    )
