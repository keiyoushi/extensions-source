package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: Array<Pair<String, String>>,
    defaultIndex: Int = 0,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    defaultIndex,
),
    UrlPartFilter {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) {
            url.addQueryParameter(urlParameter, value)
        }
    }
}

class SortFilter :
    SelectFilter(
        "ترتيب حسب",
        "sort",
        arrayOf(
            Pair("الافتراضي", ""),
            Pair("الأكثر مشاهدة", "views"),
            Pair("آخر التحديثات", "latest"),
            Pair("الأعلى تقييماً", "rating"),
            Pair("الأحدث إضافة", "created_at"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "الحالة",
        "status",
        arrayOf(
            Pair("الكل", ""),
            Pair("مستمر", "ongoing"),
            Pair("مكتمل", "completed"),
        ),
    )

class GenreFilter(genres: List<String>) :
    SelectFilter(
        "التصنيف",
        "tags",
        arrayOf(Pair("الكل", "")) + genres.map { Pair(it, it) }.toTypedArray(),
    )
