package eu.kanade.tachiyomi.extension.ar.mangaswat

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
        val field = SORT_OPTIONS[selection.index].second
        builder.addQueryParameter("order_by", if (selection.ascending) field else "-$field")
    }
}

private val SORT_OPTIONS = listOf(
    "المتابعين" to "followers_count",
    "المشاهدات" to "views_count",
    "التقييم" to "rating",
    "عدد الفصول" to "chapters_count",
    "تاريخ الإضافة" to "created_at",
    "الاسم" to "title",
)

class CheckBoxOption(name: String, val value: Int) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    private val param: String,
    options: List<Pair<String, Int>>,
) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter(param, it.value.toString()) }
    }
}

class TypeFilter :
    CheckBoxGroup(
        "النوع",
        "type",
        listOf(
            "مانهوا كوري" to 131,
            "مانها صيني" to 132,
            "مانجا ياباني" to 133,
            "ياباني ملون" to 136,
            "كوميكس" to 134,
            "ويبتون انجليزي" to 137,
        ),
    )

class StatusFilter :
    CheckBoxGroup(
        "الحالة",
        "status",
        listOf("مستمر" to 79, "مكتمل" to 80, "متوقف مؤقت" to 81),
    )

class GenreFilter(private val genres: List<GenreDto>) :
    Filter.Select<String>("التصنيف", arrayOf("الكل") + genres.map { it.name }),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state > 0) builder.addQueryParameter("genres", genres[state - 1].id.toString())
    }
}
