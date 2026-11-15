package eu.kanade.tachiyomi.extension.ar.mangaorigin

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(GenreFilter())

class GenreFilter : Filter.Select<String>("التصنيف", arrayOf("الكل") + GENRES) {
    val selected get() = GENRES.getOrNull(state - 1)
}

// The site's own list of genres
private val GENRES = arrayOf(
    "آليات", "أكشن", "ألعاب", "تاريخي", "تراجيدي", "جوسيه", "حياة مدرسية",
    "حياة يومية", "خيال", "خيال علمي", "دراما", "راشد", "رعب", "رومانسي",
    "رياضي", "زمانكاني", "سينين", "شوجو", "شونين", "غموض", "فنون قتال",
    "قوى خارقة", "كوميدي", "مغامرة", "نفسي", "وحوش", "ون شوت", "ويبتون",
)
