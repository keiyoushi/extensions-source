@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.tr.mangaship

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Kategoriler", genres)

class TypeFilter : Filter.Select<String>("Seri Tipi", arrayOf("Hepsi", "Manga", "Webtoon", "Manhwa", "Manhua", "Amatör")) {
    fun toUriPart() = arrayOf("", "1", "2", "3", "4", "5")[state]
}

class StatusFilter : Filter.Select<String>("Durum", arrayOf("Hepsi", "Devam Edenler", "Tamamlananlar")) {
    fun toUriPart() = arrayOf("", "1", "0")[state]
}

fun getFilters() = listOf(
    TypeFilter(),
    StatusFilter(),
    GenreFilter(getGenreList()),
)

private fun getGenreList() = listOf(
    Genre("Aksiyon", "20"),
    Genre("Animeli Manga", "68"),
    Genre("Bilim Kurgu", "42"),
    Genre("Büyü", "21"),
    Genre("Doğaüstü Güçler", "22"),
    Genre("Doujinshi", "60"),
    Genre("Dövüş Sanatları", "23"),
    Genre("Drama", "44"),
    Genre("Ecchi", "49"),
    Genre("Fantastik", "26"),
    Genre("Gerilim", "27"),
    Genre("Gizem", "63"),
    Genre("Harem", "52"),
    Genre("Hayattan Kesitler", "66"),
    Genre("Isekai", "47"),
    Genre("Josei", "65"),
    Genre("Komedi", "28"),
    Genre("Korku", "45"),
    Genre("Macera", "29"),
    Genre("Ofis", "67"),
    Genre("Okul", "55"),
    Genre("Oneshot", "59"),
    Genre("Oyun", "30"),
    Genre("Polisiye", "33"),
    Genre("Psikolojik", "24"),
    Genre("Romantizm", "43"),
    Genre("Spor", "46"),
    Genre("Tarihsel", "41"),
    Genre("Trajedi", "62"),
    Genre("Türk Yapımı", "64"),
    Genre("Uzay", "61"),
    Genre("Vampir", "58"),
    Genre("Webtoon", "50"),
    Genre("Yaoi", "54"),
    Genre("Yetişkin", "48"),
    Genre("Yuri", "53"),
)
