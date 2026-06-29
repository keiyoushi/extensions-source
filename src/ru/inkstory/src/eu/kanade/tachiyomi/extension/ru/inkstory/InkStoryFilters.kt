package eu.kanade.tachiyomi.extension.ru.inkstory

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

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

    fun hasActiveFilters(): Boolean = sortField != DEFAULT_SORT_FIELD ||
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

    companion object {
        const val DEFAULT_SORT_FIELD = "viewsCount"
        const val DEFAULT_SORT_ORDER = "desc"

        fun from(filters: FilterList): InkStorySearchFilters {
            val sortField = filters.firstInstanceOrNull<SortFieldFilter>()?.selectedValue ?: DEFAULT_SORT_FIELD
            val sortOrder = filters.firstInstanceOrNull<SortOrderFilter>()?.selectedValue ?: DEFAULT_SORT_ORDER
            val statuses = filters.firstInstanceOrNull<StatusFilter>()?.selectedValues.orEmpty()
            val countries = filters.firstInstanceOrNull<CountryFilter>()?.selectedValues.orEmpty()
            val contentStatuses = filters.firstInstanceOrNull<ContentStatusFilter>()?.selectedValues.orEmpty()
            val formats = filters.firstInstanceOrNull<FormatFilter>()?.selectedValues.orEmpty()
            val labelIncludes = filters.firstInstanceOrNull<GenreIncludeFilter>()?.selectedValues.orEmpty()
            val labelExcludes = filters.firstInstanceOrNull<GenreExcludeFilter>()?.selectedValues.orEmpty()
            val strictLabelEqual = filters.firstInstanceOrNull<StrictLabelEqualFilter>()?.state == true
            val ratingRange = filters.firstInstanceOrNull<RatingRangeFilter>()
            val averageRatingMin = normalizeDecimal(ratingRange?.minValue, 0.0, 10.0)
            val averageRatingMax = normalizeDecimal(ratingRange?.maxValue, 0.0, 10.0)
            val yearRange = filters.firstInstanceOrNull<YearRangeFilter>()
            val yearMin = normalizeInt(yearRange?.minValue, 1900, 2100)
            val yearMax = normalizeInt(yearRange?.maxValue, 1900, 2100)
            val chaptersRange = filters.firstInstanceOrNull<ChaptersRangeFilter>()
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

internal class SortFieldFilter :
    QuerySelectFilter(
        name = "Сортировка по",
        options = SORT_OPTIONS,
        defaultValue = InkStorySearchFilters.DEFAULT_SORT_FIELD,
    ) {
    companion object {
        private val SORT_OPTIONS = arrayOf(
            "Просмотрам" to "viewsCount",
            "Лайкам" to "likesCount",
            "Главам" to "chaptersCount",
            "Закладкам" to "bookmarksCount",
            "Рейтингу" to "averageRating",
            "Дате добавления" to "createdAt",
        )
    }
}

internal class SortOrderFilter :
    QuerySelectFilter(
        name = "Порядок",
        options = arrayOf(
            "По убыванию" to "desc",
            "По возрастанию" to "asc",
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

internal class StatusFilter :
    MultiValueFilter(
        name = "Статусы",
        values = listOf(
            "Онгоинг" to "ONGOING",
            "Завершен" to "DONE",
            "Заморожен" to "FROZEN",
            "Анонс" to "ANNOUNCE",
        ),
    )

internal class CountryFilter :
    MultiValueFilter(
        name = "Страны",
        values = listOf(
            "Россия" to "RUSSIA",
            "Япония" to "JAPAN",
            "Корея" to "KOREA",
            "Китай" to "CHINA",
            "Другое" to "OTHER",
        ),
    )

internal class ContentStatusFilter :
    MultiValueFilter(
        name = "Контент-статусы",
        values = listOf(
            "Порнографический" to "PORNOGRAPHIC",
            "Небезопасный" to "UNSAFE",
            "Безопасный" to "SAFE",
            "Эротический" to "EROTIC",
        ),
    )

internal class FormatFilter :
    MultiValueFilter(
        name = "Форматы",
        values = listOf(
            "Енкома" to "FOURTH_KOMA",
            "Сборник" to "COMPILATION",
            "Додзинси" to "DOUJINSHI",
            "Вебтун" to "WEBTOON",
            "Цветной" to "COLORED",
            "Артбук" to "ARTBOOK",
            "Сингл" to "SINGLE",
            "Лайт" to "LIGHT",
            "Веб" to "WEB",
        ),
    )

internal class GenreIncludeFilter :
    MultiValueFilter(
        name = "Жанры (включить)",
        values = GENRE_VALUES,
    )

internal class GenreExcludeFilter :
    MultiValueFilter(
        name = "Жанры (исключить)",
        values = GENRE_VALUES,
    )

internal class StrictLabelEqualFilter :
    Filter.CheckBox(
        "Строгое совпадение жанров",
    )

internal class RatingMinFilter : Filter.Text("От")
internal class RatingMaxFilter : Filter.Text("До")

internal class RatingRangeFilter :
    Filter.Group<Filter<*>>(
        name = "Рейтинг",
        state = listOf(RatingMinFilter(), RatingMaxFilter()),
    ) {
    val minValue: String
        get() = (state[0] as RatingMinFilter).state
    val maxValue: String
        get() = (state[1] as RatingMaxFilter).state
}

internal class YearMinFilter : Filter.Text("От")
internal class YearMaxFilter : Filter.Text("До")

internal class YearRangeFilter :
    Filter.Group<Filter<*>>(
        name = "Год выпуска",
        state = listOf(YearMinFilter(), YearMaxFilter()),
    ) {
    val minValue: String
        get() = (state[0] as YearMinFilter).state
    val maxValue: String
        get() = (state[1] as YearMaxFilter).state
}

internal class ChaptersCountMinFilter : Filter.Text("От")
internal class ChaptersCountMaxFilter : Filter.Text("До")

internal class ChaptersRangeFilter :
    Filter.Group<Filter<*>>(
        name = "Количество глав",
        state = listOf(ChaptersCountMinFilter(), ChaptersCountMaxFilter()),
    ) {
    val minValue: String
        get() = (state[0] as ChaptersCountMinFilter).state
    val maxValue: String
        get() = (state[1] as ChaptersCountMaxFilter).state
}

private val GENRE_VALUES = listOf(
    "арт" to "art",
    "боевик" to "action",
    "боевые искусства" to "martial_arts",
    "вампиры" to "vampires",
    "гарем" to "harem",
    "гендерная интрига" to "gender_intriga",
    "детектив" to "detective",
    "дзёсэй" to "josei",
    "драма" to "drama",
    "игра" to "game",
    "исекай" to "isekai",
    "история" to "historical",
    "киберпанк" to "cyberpunk",
    "кодомо" to "codomo",
    "комедия" to "comedy",
    "махо-сёдзё" to "maho_shoujo",
    "меха" to "mecha",
    "мистика" to "mystery",
    "научная фантастика" to "sci_fi",
    "омегаверс" to "omegavers",
    "повседневность" to "natural",
    "постапокалиптика" to "postapocalypse",
    "приключения" to "adventure",
    "психология" to "psychological",
    "романтика" to "romance",
    "самурайский боевик" to "samurai",
    "сверхъестественное" to "supernatural",
    "сёдзё" to "shoujo",
    "сёнэн" to "shounen",
    "спорт" to "sports",
    "сэйнэн" to "seinen",
    "трагедия" to "tragedy",
    "триллер" to "thriller",
    "ужасы" to "horror",
    "фантастика" to "fantastic",
    "фэнтези" to "fantasy",
    "школа" to "school",
    "эротика" to "erotica",
    "этти" to "ecchi",
)
