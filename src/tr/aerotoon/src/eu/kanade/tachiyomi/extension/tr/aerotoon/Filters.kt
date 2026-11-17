package eu.kanade.tachiyomi.extension.tr.aerotoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : Filter.Select<String>("Sıralama", SORTS.map { it.first }.toTypedArray(), 1) {
    val value get() = SORTS[state].second
}

class StatusFilter : Filter.Select<String>("Durum", STATUSES.map { it.first }.toTypedArray()) {
    val value get() = STATUSES[state].second
}

class TypeFilter : Filter.Select<String>("Format", TYPES.map { it.first }.toTypedArray()) {
    val value get() = TYPES[state].second
}

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter : Filter.Group<Genre>("Türler", GENRES.map { Genre(it.label, it.name) }) {
    val selected get() = state.filter { it.state }.map { it.value }
}

fun getFilters() = FilterList(
    SortFilter(),
    StatusFilter(),
    TypeFilter(),
    GenreFilter(),
)

class GenreInfo(val label: String, val name: String, val slug: String)

private val SORTS = listOf(
    "Son Güncellenen" to "latest",
    "En Popüler" to "popular",
    "En Yüksek Puan" to "rating",
    "A-Z" to "title",
)

private val STATUSES = listOf(
    "Tümü" to null,
    "Devam Ediyor" to "ongoing",
    "Güncel" to "current",
    "Tamamlandı" to "completed",
    "Ara Verildi" to "hiatus",
    "İptal Edildi" to "cancelled",
)

private val TYPES = listOf(
    "Tümü" to null,
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Çizgi Roman" to "comic",
)

// The site's genre menu: Turkish labels, English values, and the slugs of its /tur/ pages
val GENRES = listOf(
    GenreInfo("Aksiyon", "Action", "aksiyon"),
    GenreInfo("Macera", "Adventure", "macera"),
    GenreInfo("Komedi", "Comedy", "komedi"),
    GenreInfo("Drama", "Drama", "drama"),
    GenreInfo("Fantastik", "Fantasy", "fantastik"),
    GenreInfo("Tarihi", "Historical", "tarihi"),
    GenreInfo("Korku", "Horror", "korku"),
    GenreInfo("Isekai", "Isekai", "isekai"),
    GenreInfo("Dövüş Sanatları", "Martial Arts", "dovus-sanatlari"),
    GenreInfo("Gizem", "Mystery", "gizem"),
    GenreInfo("Reenkarnasyon", "Reincarnation", "reenkarnasyon"),
    GenreInfo("Romantik", "Romance", "romantik"),
    GenreInfo("Okul", "School", "okul"),
    GenreInfo("Bilim Kurgu", "Sci-Fi", "bilim-kurgu"),
    GenreInfo("Doğaüstü", "Supernatural", "dogaustu"),
    GenreInfo("Gerilim", "Thriller", "gerilim"),
    GenreInfo("Ecchi", "Ecchi", "ecchi"),
    GenreInfo("Harem", "Harem", "harem"),
    GenreInfo("Josei", "Josei", "josei"),
    GenreInfo("Yetişkin", "Mature", "yetiskin"),
    GenreInfo("Mecha", "Mecha", "mecha"),
    GenreInfo("Psikolojik", "Psychological", "psikolojik"),
    GenreInfo("Seinen", "Seinen", "seinen"),
    GenreInfo("Shoujo", "Shoujo", "shoujo"),
    GenreInfo("Shounen", "Shounen", "shounen"),
    GenreInfo("Günlük Yaşam", "Slice of Life", "gunluk-yasam"),
    GenreInfo("Spor", "Sports", "spor"),
    GenreInfo("Trajedi", "Tragedy", "trajedi"),
    GenreInfo("Webtoon", "Webtoon", "webtoon"),
    GenreInfo("Manhwa", "Manhwa", "manhwa"),
    GenreInfo("Manhua", "Manhua", "manhua"),
)
