package eu.kanade.tachiyomi.extension.tr.mangitto

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(genres: List<String>) :
    Filter.Group<Filter.CheckBox>(
        "Manga Türü",
        genres.map { object : Filter.CheckBox(it) {} },
    ) {
    fun getQuery() = state.filter { it.state }.joinToString(",") { it.name }
}

class AdultFilter : Filter.CheckBox("Yetişkinlere yönelik içerik", false)
class CompletedFilter : Filter.CheckBox("Tamamlanmış seri", false)
class ScoreFilter : Filter.Text("Minimum Puan (0-100)")
class DateFilter : Filter.Text("Minimum Çıkış Yılı (Örn: 2020)")
