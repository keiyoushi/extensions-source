package eu.kanade.tachiyomi.extension.ru.inkstory

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal data class InkStorySearchFilters(
    val sortField: String = DEFAULT_SORT_FIELD,
    val sortOrder: String = DEFAULT_SORT_ORDER,
    val statuses: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val contentStatuses: List<String> = emptyList(),
    val formats: List<String> = emptyList(),
    val labelIncludes: List<String> = emptyList(),
    val labelExcludes: List<String> = emptyList(),
    val strictLabelEqual: Boolean = false,
    val averageRatingMin: String? = null,
    val averageRatingMax: String? = null,
    val yearMin: String? = null,
    val yearMax: String? = null,
    val chaptersCountMin: String? = null,
    val chaptersCountMax: String? = null,
) {
    val sort: String
        get() = "$sortField,$sortOrder"

    fun hasActiveFilters(): Boolean {
        return sortField != DEFAULT_SORT_FIELD ||
            sortOrder != DEFAULT_SORT_ORDER ||
            statuses.isNotEmpty() ||
            countries.isNotEmpty() ||
            contentStatuses.isNotEmpty() ||
            formats.isNotEmpty() ||
            labelIncludes.isNotEmpty() ||
            labelExcludes.isNotEmpty() ||
            strictLabelEqual ||
            averageRatingMin != null ||
            averageRatingMax != null ||
            yearMin != null ||
            yearMax != null ||
            chaptersCountMin != null ||
            chaptersCountMax != null
    }

    companion object {
        const val DEFAULT_SORT_FIELD = "viewsCount"
        const val DEFAULT_SORT_ORDER = "desc"

        fun from(filters: FilterList): InkStorySearchFilters {
            val sortField = filters.filterIsInstance<SortFieldFilter>().firstOrNull()?.selectedValue ?: DEFAULT_SORT_FIELD
            val sortOrder = filters.filterIsInstance<SortOrderFilter>().firstOrNull()?.selectedValue ?: DEFAULT_SORT_ORDER
            val statuses = filters.filterIsInstance<StatusFilter>().firstOrNull()?.selectedValues.orEmpty()
            val countries = filters.filterIsInstance<CountryFilter>().firstOrNull()?.selectedValues.orEmpty()
            val contentStatuses = filters.filterIsInstance<ContentStatusFilter>().firstOrNull()?.selectedValues.orEmpty()
            val formats = filters.filterIsInstance<FormatFilter>().firstOrNull()?.selectedValues.orEmpty()
            val labelIncludes = filters.filterIsInstance<GenreIncludeFilter>().firstOrNull()?.selectedValues.orEmpty()
            val labelExcludes = filters.filterIsInstance<GenreExcludeFilter>().firstOrNull()?.selectedValues.orEmpty()
            val strictLabelEqual = filters.filterIsInstance<StrictLabelEqualFilter>().firstOrNull()?.state == true
            val ratingRange = filters.filterIsInstance<RatingRangeFilter>().firstOrNull()
            val averageRatingMin = normalizeDecimal(ratingRange?.minValue, 0.0, 10.0)
            val averageRatingMax = normalizeDecimal(ratingRange?.maxValue, 0.0, 10.0)
            val yearRange = filters.filterIsInstance<YearRangeFilter>().firstOrNull()
            val yearMin = normalizeInt(yearRange?.minValue, 1900, 2100)
            val yearMax = normalizeInt(yearRange?.maxValue, 1900, 2100)
            val chaptersRange = filters.filterIsInstance<ChaptersRangeFilter>().firstOrNull()
            val chaptersCountMin = normalizeInt(chaptersRange?.minValue, 0, 100000)
            val chaptersCountMax = normalizeInt(chaptersRange?.maxValue, 0, 100000)

            return InkStorySearchFilters(
                sortField = sortField,
                sortOrder = sortOrder,
                statuses = statuses,
                countries = countries,
                contentStatuses = contentStatuses,
                formats = formats,
                labelIncludes = labelIncludes,
                labelExcludes = labelExcludes,
                strictLabelEqual = strictLabelEqual,
                averageRatingMin = averageRatingMin,
                averageRatingMax = averageRatingMax,
                yearMin = yearMin,
                yearMax = yearMax,
                chaptersCountMin = chaptersCountMin,
                chaptersCountMax = chaptersCountMax,
            )
        }

        private fun normalizeDecimal(rawValue: String?, min: Double, max: Double): String? {
            val value = rawValue?.trim()?.replace(',', '.')?.takeIf(String::isNotEmpty) ?: return null
            val parsed = value.toDoubleOrNull() ?: return null
            if (parsed < min || parsed > max) return null
            return if (parsed % 1.0 == 0.0) parsed.toLong().toString() else parsed.toString()
        }

        private fun normalizeInt(rawValue: String?, min: Int, max: Int): String? {
            val value = rawValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val parsed = value.toIntOrNull() ?: return null
            if (parsed < min || parsed > max) return null
            return parsed.toString()
        }
    }
}

internal abstract class QuerySelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
    defaultValue: String,
) : Filter.Select<String>(
    name = name,
    values = options.map { it.first }.toTypedArray(),
    state = options.indexOfFirst { it.second == defaultValue }.takeIf { it >= 0 } ?: 0,
) {
    val selectedValue: String
        get() = options[state].second
}

internal class SortFieldFilter : QuerySelectFilter(
    name = "\u0421\u043E\u0440\u0442\u0438\u0440\u043E\u0432\u043A\u0430 \u043F\u043E",
    options = SORT_OPTIONS,
    defaultValue = InkStorySearchFilters.DEFAULT_SORT_FIELD,
) {
    companion object {
        private val SORT_OPTIONS = arrayOf(
            "\u041F\u0440\u043E\u0441\u043C\u043E\u0442\u0440\u0430\u043C" to "viewsCount",
            "\u041B\u0430\u0439\u043A\u0430\u043C" to "likesCount",
            "\u0413\u043B\u0430\u0432\u0430\u043C" to "chaptersCount",
            "\u0417\u0430\u043A\u043B\u0430\u0434\u043A\u0430\u043C" to "bookmarksCount",
            "\u0420\u0435\u0439\u0442\u0438\u043D\u0433\u0443" to "averageRating",
            "\u0414\u0430\u0442\u0435 \u0434\u043E\u0431\u0430\u0432\u043B\u0435\u043D\u0438\u044F" to "createdAt",
        )
    }
}

internal class SortOrderFilter : QuerySelectFilter(
    name = "\u041F\u043E\u0440\u044F\u0434\u043E\u043A",
    options = arrayOf(
        "\u041F\u043E \u0443\u0431\u044B\u0432\u0430\u043D\u0438\u044E" to "desc",
        "\u041F\u043E \u0432\u043E\u0437\u0440\u0430\u0441\u0442\u0430\u043D\u0438\u044E" to "asc",
    ),
    defaultValue = InkStorySearchFilters.DEFAULT_SORT_ORDER,
)

internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    values: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = values.map { MultiValueOption(it.first, it.second) },
) {
    val selectedValues: List<String>
        get() = state.filter { it.state }.map { it.value }
}

internal class StatusFilter : MultiValueFilter(
    name = "\u0421\u0442\u0430\u0442\u0443\u0441\u044B",
    values = listOf(
        "\u041E\u043D\u0433\u043E\u0438\u043D\u0433" to "ONGOING",
        "\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043D" to "DONE",
        "\u0417\u0430\u043C\u043E\u0440\u043E\u0436\u0435\u043D" to "FROZEN",
        "\u0410\u043D\u043E\u043D\u0441" to "ANNOUNCE",
    ),
)

internal class CountryFilter : MultiValueFilter(
    name = "\u0421\u0442\u0440\u0430\u043D\u044B",
    values = listOf(
        "\u0420\u043E\u0441\u0441\u0438\u044F" to "RUSSIA",
        "\u042F\u043F\u043E\u043D\u0438\u044F" to "JAPAN",
        "\u041A\u043E\u0440\u0435\u044F" to "KOREA",
        "\u041A\u0438\u0442\u0430\u0439" to "CHINA",
        "\u0414\u0440\u0443\u0433\u043E\u0435" to "OTHER",
    ),
)

internal class ContentStatusFilter : MultiValueFilter(
    name = "\u041A\u043E\u043D\u0442\u0435\u043D\u0442-\u0441\u0442\u0430\u0442\u0443\u0441\u044B",
    values = listOf(
        "\u041F\u043E\u0440\u043D\u043E\u0433\u0440\u0430\u0444\u0438\u0447\u0435\u0441\u043A\u0438\u0439" to "PORNOGRAPHIC",
        "\u041D\u0435\u0431\u0435\u0437\u043E\u043F\u0430\u0441\u043D\u044B\u0439" to "UNSAFE",
        "\u0411\u0435\u0437\u043E\u043F\u0430\u0441\u043D\u044B\u0439" to "SAFE",
        "\u042D\u0440\u043E\u0442\u0438\u0447\u0435\u0441\u043A\u0438\u0439" to "EROTIC",
    ),
)

internal class FormatFilter : MultiValueFilter(
    name = "\u0424\u043E\u0440\u043C\u0430\u0442\u044B",
    values = listOf(
        "\u0415\u043D\u043A\u043E\u043C\u0430" to "FOURTH_KOMA",
        "\u0421\u0431\u043E\u0440\u043D\u0438\u043A" to "COMPILATION",
        "\u0414\u043E\u0434\u0437\u0438\u043D\u0441\u0438" to "DOUJINSHI",
        "\u0412\u0435\u0431\u0442\u0443\u043D" to "WEBTOON",
        "\u0426\u0432\u0435\u0442\u043D\u043E\u0439" to "COLORED",
        "\u0410\u0440\u0442\u0431\u0443\u043A" to "ARTBOOK",
        "\u0421\u0438\u043D\u0433\u043B" to "SINGLE",
        "\u041B\u0430\u0439\u0442" to "LIGHT",
        "\u0412\u0435\u0431" to "WEB",
    ),
)

internal class GenreIncludeFilter : MultiValueFilter(
    name = "\u0416\u0430\u043D\u0440\u044B (\u0432\u043A\u043B\u044E\u0447\u0438\u0442\u044C)",
    values = GENRE_VALUES,
)

internal class GenreExcludeFilter : MultiValueFilter(
    name = "\u0416\u0430\u043D\u0440\u044B (\u0438\u0441\u043A\u043B\u044E\u0447\u0438\u0442\u044C)",
    values = GENRE_VALUES,
)

internal class StrictLabelEqualFilter : Filter.CheckBox(
    "\u0421\u0442\u0440\u043E\u0433\u043E\u0435 \u0441\u043E\u0432\u043F\u0430\u0434\u0435\u043D\u0438\u0435 \u0436\u0430\u043D\u0440\u043E\u0432",
)

internal class RatingMinFilter : Filter.Text("\u041E\u0442")
internal class RatingMaxFilter : Filter.Text("\u0414\u043E")

internal class RatingRangeFilter : Filter.Group<Filter<*>>(
    name = "\u0420\u0435\u0439\u0442\u0438\u043D\u0433",
    state = listOf(RatingMinFilter(), RatingMaxFilter()),
) {
    val minValue: String
        get() = (state[0] as RatingMinFilter).state
    val maxValue: String
        get() = (state[1] as RatingMaxFilter).state
}

internal class YearMinFilter : Filter.Text("\u041E\u0442")
internal class YearMaxFilter : Filter.Text("\u0414\u043E")

internal class YearRangeFilter : Filter.Group<Filter<*>>(
    name = "\u0413\u043E\u0434 \u0432\u044B\u043F\u0443\u0441\u043A\u0430",
    state = listOf(YearMinFilter(), YearMaxFilter()),
) {
    val minValue: String
        get() = (state[0] as YearMinFilter).state
    val maxValue: String
        get() = (state[1] as YearMaxFilter).state
}

internal class ChaptersCountMinFilter : Filter.Text("\u041E\u0442")
internal class ChaptersCountMaxFilter : Filter.Text("\u0414\u043E")

internal class ChaptersRangeFilter : Filter.Group<Filter<*>>(
    name = "\u041A\u043E\u043B\u0438\u0447\u0435\u0441\u0442\u0432\u043E \u0433\u043B\u0430\u0432",
    state = listOf(ChaptersCountMinFilter(), ChaptersCountMaxFilter()),
) {
    val minValue: String
        get() = (state[0] as ChaptersCountMinFilter).state
    val maxValue: String
        get() = (state[1] as ChaptersCountMaxFilter).state
}

private val GENRE_VALUES = listOf(
    "\u0430\u0440\u0442" to "art",
    "\u0431\u043E\u0435\u0432\u0438\u043A" to "action",
    "\u0431\u043E\u0435\u0432\u044B\u0435 \u0438\u0441\u043A\u0443\u0441\u0441\u0442\u0432\u0430" to "martial_arts",
    "\u0432\u0430\u043C\u043F\u0438\u0440\u044B" to "vampires",
    "\u0433\u0430\u0440\u0435\u043C" to "harem",
    "\u0433\u0435\u043D\u0434\u0435\u0440\u043D\u0430\u044F \u0438\u043D\u0442\u0440\u0438\u0433\u0430" to "gender_intriga",
    "\u0434\u0435\u0442\u0435\u043A\u0442\u0438\u0432" to "detective",
    "\u0434\u0437\u0451\u0441\u044D\u0439" to "josei",
    "\u0434\u0440\u0430\u043C\u0430" to "drama",
    "\u0438\u0433\u0440\u0430" to "game",
    "\u0438\u0441\u0435\u043A\u0430\u0439" to "isekai",
    "\u0438\u0441\u0442\u043E\u0440\u0438\u044F" to "historical",
    "\u043A\u0438\u0431\u0435\u0440\u043F\u0430\u043D\u043A" to "cyberpunk",
    "\u043A\u043E\u0434\u043E\u043C\u043E" to "codomo",
    "\u043A\u043E\u043C\u0435\u0434\u0438\u044F" to "comedy",
    "\u043C\u0430\u0445\u043E-\u0441\u0451\u0434\u0437\u0451" to "maho_shoujo",
    "\u043C\u0435\u0445\u0430" to "mecha",
    "\u043C\u0438\u0441\u0442\u0438\u043A\u0430" to "mystery",
    "\u043D\u0430\u0443\u0447\u043D\u0430\u044F \u0444\u0430\u043D\u0442\u0430\u0441\u0442\u0438\u043A\u0430" to "sci_fi",
    "\u043E\u043C\u0435\u0433\u0430\u0432\u0435\u0440\u0441" to "omegavers",
    "\u043F\u043E\u0432\u0441\u0435\u0434\u043D\u0435\u0432\u043D\u043E\u0441\u0442\u044C" to "natural",
    "\u043F\u043E\u0441\u0442\u0430\u043F\u043E\u043A\u0430\u043B\u0438\u043F\u0442\u0438\u043A\u0430" to "postapocalypse",
    "\u043F\u0440\u0438\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u044F" to "adventure",
    "\u043F\u0441\u0438\u0445\u043E\u043B\u043E\u0433\u0438\u044F" to "psychological",
    "\u0440\u043E\u043C\u0430\u043D\u0442\u0438\u043A\u0430" to "romance",
    "\u0441\u0430\u043C\u0443\u0440\u0430\u0439\u0441\u043A\u0438\u0439 \u0431\u043E\u0435\u0432\u0438\u043A" to "samurai",
    "\u0441\u0432\u0435\u0440\u0445\u044A\u0435\u0441\u0442\u0435\u0441\u0442\u0432\u0435\u043D\u043D\u043E\u0435" to "supernatural",
    "\u0441\u0451\u0434\u0437\u0451" to "shoujo",
    "\u0441\u0451\u043D\u044D\u043D" to "shounen",
    "\u0441\u043F\u043E\u0440\u0442" to "sports",
    "\u0441\u044D\u0439\u043D\u044D\u043D" to "seinen",
    "\u0442\u0440\u0430\u0433\u0435\u0434\u0438\u044F" to "tragedy",
    "\u0442\u0440\u0438\u043B\u043B\u0435\u0440" to "thriller",
    "\u0443\u0436\u0430\u0441\u044B" to "horror",
    "\u0444\u0430\u043D\u0442\u0430\u0441\u0442\u0438\u043A\u0430" to "fantastic",
    "\u0444\u044D\u043D\u0442\u0435\u0437\u0438" to "fantasy",
    "\u0448\u043A\u043E\u043B\u0430" to "school",
    "\u044D\u0440\u043E\u0442\u0438\u043A\u0430" to "erotica",
    "\u044D\u0442\u0442\u0438" to "ecchi",
)
