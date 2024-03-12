package eu.kanade.tachiyomi.multisrc.gmanga

import eu.kanade.tachiyomi.source.model.Filter
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class TagFilterData(
    private val id: String,
    private val name: String,
    private val state: Int = Filter.TriState.STATE_IGNORE,
) {
    fun toTagFilter() = TagFilter(id, name, state)
}

class TagFilter(
    val id: String,
    name: String,
    state: Int = STATE_IGNORE,
) : Filter.TriState(name, state)

abstract class ValidatingTextFilter(name: String) : Filter.Text(name) {
    abstract fun isValid(): Boolean
}

private val DATE_FITLER_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).apply {
    isLenient = false
}

private fun SimpleDateFormat.isValid(date: String): Boolean {
    return try {
        parse(date)
        true
    } catch (e: ParseException) {
        false
    }
}

class DateFilter(val id: String, name: String) : ValidatingTextFilter("(yyyy/MM/dd) $name)") {
    override fun isValid(): Boolean = DATE_FITLER_FORMAT.isValid(state)
}

class IntFilter(val id: String, name: String) : ValidatingTextFilter(name) {
    override fun isValid(): Boolean = state.toIntOrNull() != null
}

class MangaTypeFilter(types: List<TagFilterData>) : Filter.Group<TagFilter>(
    "الأصل",
    types.map { it.toTagFilter() },
)

class OneShotFilter : Filter.Group<TagFilter>(
    "ونشوت؟",
    listOf(
        TagFilter("oneshot", "نعم", TriState.STATE_EXCLUDE),
    ),
)

class StoryStatusFilter(status: List<TagFilterData>) : Filter.Group<TagFilter>(
    "حالة القصة",
    status.map { it.toTagFilter() },
)

class TranslationStatusFilter(tlStatus: List<TagFilterData>) : Filter.Group<TagFilter>(
    "حالة الترجمة",
    tlStatus.map { it.toTagFilter() },
)

class ChapterCountFilter : Filter.Group<IntFilter>(
    "عدد الفصول",
    listOf(
        IntFilter("min", "على الأقل"),
        IntFilter("max", "على الأكثر"),
    ),
) {
    val min get() = state.first { it.id == "min" }
    val max get() = state.first { it.id == "max" }
}

class DateRangeFilter : Filter.Group<DateFilter>(
    "تاريخ النشر",
    listOf(
        DateFilter("start", "تاريخ النشر"),
        DateFilter("end", "تاريخ الإنتهاء"),
    ),
) {
    val start get() = state.first { it.id == "start" }
    val end get() = state.first { it.id == "end" }
}

class CategoryFilter(categories: List<TagFilterData>) : Filter.Group<TagFilter>(
    "التصنيفات",
    categories.map { it.toTagFilter() },
)
