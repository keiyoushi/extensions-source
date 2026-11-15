package eu.kanade.tachiyomi.extension.ar.peachbl

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("الترتيب", SORTS.map { it.first }.toTypedArray(), 2) {
    fun selected() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "آخر تحديث" to "latest_chapter",
            "أحدث إضافة" to "latest_webtoon",
            "الأكثر مشاهدة" to "most_viewed",
            "الأعلى تقييماً" to "top_rated",
            "الأكثر فصولاً" to "most_chapters",
            "أبجدياً" to "title",
            "عشوائي" to "random",
        )
    }
}

class ContentFilter : Filter.Select<String>("المحتوى", arrayOf("الكل", "آمن", "للكبار +18")) {
    fun selected() = listOf("", "safe", "adult")[state]
}

class ValueCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class TypeFilter :
    Filter.Group<ValueCheckBox>(
        "النوع",
        listOf("comic", "manga", "manha", "manhwa").map { ValueCheckBox(it, it) },
    )

class StatusFilter :
    Filter.Group<ValueCheckBox>(
        "الحالة",
        listOf("completed", "ongoing", "stopped").map { ValueCheckBox(it, it) },
    )

class GenreTriState(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter(genres: Map<String, String>) : Filter.Group<GenreTriState>("التصنيفات", genres.map { (name, slug) -> GenreTriState(name, slug) })
