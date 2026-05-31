package eu.kanade.tachiyomi.extension.tr.mangitto

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter : Filter.Select<String>("Manga Türü", genres.map { it.first }.toTypedArray()) {
    fun getQuery() = genres[state].second
}

private val genres = arrayOf(
    Pair("Tümü", ""),
    Pair("Action", "Action"),
    Pair("Adventure", "Adventure"),
    Pair("Comedy", "Comedy"),
    Pair("Drama", "Drama"),
    Pair("Ecchi", "Ecchi"),
    Pair("Fantasy", "Fantasy"),
    Pair("Horror", "Horror"),
    Pair("Mecha", "Mecha"),
    Pair("Mystery", "Mystery"),
    Pair("Psychological", "Psychological"),
    Pair("Romance", "Romance"),
    Pair("Sci-Fi", "Sci-Fi"),
    Pair("Slice of Life", "Slice of Life"),
    Pair("Supernatural", "Supernatural"),
    Pair("Thriller", "Thriller"),
)

class AdultFilter : Filter.CheckBox("Yetişkinlere yönelik içerik", false)
class CompletedFilter : Filter.CheckBox("Tamamlanmış seri", false)
class ScoreFilter : Filter.Text("Kullanıcı Puanı (0-100)")
class DateFilter : Filter.Text("Çıkış Tarihi (Örn: 1880-2024)")
