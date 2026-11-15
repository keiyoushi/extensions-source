package eu.kanade.tachiyomi.extension.tr.mangacix

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sıralama", SORT_NAMES) {
    val selected: String
        get() = SORT_VALUES[state]

    companion object {
        private val SORT_NAMES = arrayOf(
            "Popülerlik",
            "Eklenme Tarihi",
            "Yayın Tarihi",
        )
        private val SORT_VALUES = arrayOf(
            "popularity",
            "created_at",
            "release_date",
        )
    }
}

class KeywordCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class KeywordFilter :
    Filter.Group<KeywordCheckBox>(
        "Türler",
        KEYWORDS.map { KeywordCheckBox(it.first, it.second) },
    )

private val KEYWORDS = listOf(
    "Tarihi" to "history",
    "Büyü" to "magic",
    "Spor" to "sport",
    "İsekai" to "isekai",
    "Askeri" to "military",
    "Dedektif" to "detective",
    "Ölüm" to "death",
    "Gizli Organizasyon" to "secret organization",
    "Ecchi" to "ecchi",
    "Harem" to "harem",
    "Ters Harem" to "reverse harem",
    "Vampir" to "vampire",
    "Kan, vahşet" to "blood",
    "Shounen" to "shounen",
    "Shounen Ai" to "shounen ai",
    "Seinen" to "seinen",
    "Canavar" to "monster",
    "Doğaüstü" to "supernatural",
    "Şeytan" to "demon",
    "İntikam" to "revenge",
    "Zaman yolculuğu" to "time travel",
    "Okul" to "school",
    "Uzay" to "space",
    "Shoujo" to "shoujo",
    "Oyun" to "game",
    "Samuray" to "samurai",
    "Ninja" to "ninja",
    "Yaşamdan Kesitler" to "slice of life",
    "İş Hayatı" to "working life",
    "Dövüş Sanatları" to "martial arts",
    "Yuri" to "yuri",
    "Yaoi" to "yaoi",
)
