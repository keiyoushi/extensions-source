package eu.kanade.tachiyomi.extension.tr.sleptmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sıralama",
        arrayOf(
            "Son Güncellenen" to "latest",
            "Popülerlik" to "popular",
            "Yüksek Puan" to "rating",
            "Görüntülenme" to "views",
            "İsim (A-Z)" to "name",
            "İsim (Z-A)" to "name_desc",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Durum",
        arrayOf(
            "Tümü" to "all",
            "Devam Ediyor" to "ongoing",
            "Tamamlandı" to "completed",
            "Ara Verildi" to "hiatus",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tür",
        arrayOf(
            "Tümü" to "all",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilterGroup(genres: List<Genre>) : Filter.Group<Genre>("Kategoriler", genres)

fun getGenreList(): List<Genre> = listOf(
    Genre("Fantastik", "fantastik"),
    Genre("Romantizm", "romantizm"),
    Genre("Drama", "drama"),
    Genre("Komedi", "komedi"),
    Genre("Aksiyon", "aksiyon"),
    Genre("Romantik", "romantik"),
    Genre("One-Shot", "one-shot"),
    Genre("Okul", "okul"),
    Genre("Korku", "korku"),
    Genre("Macera", "macera"),
    Genre("Doğaüstü", "dogaustu"),
    Genre("Shoujo", "shoujo"),
    Genre("Isekai", "isekai"),
    Genre("Shounen", "shounen"),
    Genre("Fantezi", "fantezi"),
    Genre("Dram", "dram"),
    Genre("Tragedy", "tragedy"),
    Genre("Suç", "suc"),
    Genre("Trajedi", "trajedi"),
    Genre("Romance", "romance"),
    Genre("Gizem", "gizem"),
    Genre("İntikam", "i-ntikam"),
    Genre("Politika", "politika"),
    Genre("Bilim Kurgu", "bilim-kurgu"),
    Genre("Okul Hayatı", "okul-hayati"),
)
