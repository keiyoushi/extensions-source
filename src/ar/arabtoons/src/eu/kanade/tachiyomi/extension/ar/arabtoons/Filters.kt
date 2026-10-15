package eu.kanade.tachiyomi.extension.ar.arabtoons

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Sort(
        "الترتيب حسب",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
        Selection(0, false),
    ),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val selection = state ?: return
        builder.addQueryParameter("sort", SORT_OPTIONS[selection.index].second)
        if (selection.ascending) builder.addQueryParameter("direction", "asc")
    }
}

private val SORT_OPTIONS = listOf(
    "أفضل تطابق" to "match",
    "آخر فصل" to "last_chapter_published_at",
    "تاريخ الإضافة" to "created_at",
    "الأعلى تقييماً" to "average_rating",
    "المشاهدات" to "views_count",
    "مشاهدات 90 يوم" to "views_90d",
    "مشاهدات 30 يوم" to "views_30d",
    "مشاهدات أسبوع" to "views_7d",
    "المتابعين" to "followers_count",
    "الاسم" to "title",
    "سنة الإصدار" to "year",
)

class CheckBoxOption(name: String, val value: Int) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    private val param: String,
    options: List<Pair<String, Int>>,
) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }
        if (checked.isNotEmpty()) builder.addQueryParameter(param, checked.joinToString(",") { it.value.toString() })
    }
}

class TypeFilter :
    CheckBoxGroup(
        "النوع",
        "type",
        listOf("مانجا" to 1, "مانهوا" to 2, "كوميكس" to 3),
    )

class StatusFilter :
    CheckBoxGroup(
        "الحالة",
        "status",
        listOf("مستمر" to 1, "مكتمل" to 2, "متوقف" to 4, "ملغى" to 3),
    )

class TriStateOption(name: String, val value: Int) : Filter.TriState(name)

class GenreFilter(genres: List<FilterOptionDto>) :
    Filter.Group<TriStateOption>("التصنيفات", genres.map { TriStateOption(it.label, it.value) }),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val included = state.filter { it.isIncluded() }
        val excluded = state.filter { it.isExcluded() }
        if (included.isNotEmpty()) builder.addQueryParameter("genres", included.joinToString(",") { it.value.toString() })
        if (excluded.isNotEmpty()) builder.addQueryParameter("genres_ex", excluded.joinToString(",") { it.value.toString() })
    }
}

class GenreModeFilter :
    Filter.Select<String>("مطابقة التصنيفات", arrayOf("أو", "و")),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state == 1) builder.addQueryParameter("genres_mode", "and")
    }
}
