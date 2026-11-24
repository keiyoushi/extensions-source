package eu.kanade.tachiyomi.extension.all.pornpics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.lib.i18n.Intl

class SortSelector(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
    val isRecent get() = state == 1
}

enum class CategoryType {
    CATEGORY,
    TAG,
    PORN_STAR,
    CHANNEL,
}

data class CategoryOption(val name: String, val type: CategoryType, val urlPart: String) {
    override fun toString() = this.name
}

data class ActiveCategoryOption(val name: String, val categoryType: CategoryType) {
    override fun toString() = this.name
}

class ActiveCategoryTypeSelector(name: String, values: Array<ActiveCategoryOption>) : Filter.Select<ActiveCategoryOption>(name, values) {
    fun selectedCategoryOption(filters: FilterList): CategoryOption? {
        val selected = values.getOrNull(state)?.categoryType ?: return null
        return filters.filterIsInstance<CategorySelector>()
            .firstOrNull { it.type == selected }
            ?.selected()
    }
}

class CategorySelector(name: String, val type: CategoryType, values: Array<CategoryOption>) : Filter.Select<CategoryOption>(name, values) {
    fun selected() = values.getOrNull(state)
}

fun createSortSelector(intl: Intl) = SortSelector(
    intl["filter.time.title"],
    arrayOf(
        intl["filter.time.option.popular"],
        intl["filter.time.option.recent"],
    ),
)

fun createActiveCategoryTypeSelector(intl: Intl) = ActiveCategoryTypeSelector(
    intl["filter.active-category-type.title"],
    arrayOf(
        ActiveCategoryOption(intl["filter.active-category-type.option.categories"], CategoryType.CATEGORY),
        ActiveCategoryOption(intl["filter.active-category-type.option.tags"], CategoryType.TAG),
        ActiveCategoryOption(intl["filter.active-category-type.option.porn-star"], CategoryType.PORN_STAR),
        ActiveCategoryOption(intl["filter.active-category-type.option.channels"], CategoryType.CHANNEL),
    ),
)

internal class NetworkFilters(
    private val intl: Intl,
    private val data: FilterData,
) {
    fun createCategorySelector(): CategorySelector? = createSelector(
        data.categories,
        intl["filter.category-type.categories.title"],
        CategoryType.CATEGORY,
    )

    fun createTagSelector(): CategorySelector? = createSelector(
        data.tags,
        intl["filter.category-type.tags.title"],
        CategoryType.TAG,
    )

    fun createPornStarSelector(): CategorySelector? = createSelector(
        data.pornStars,
        intl["filter.category-type.porn-star.title"],
        CategoryType.PORN_STAR,
    )

    fun createChannelSelector(): CategorySelector? = createSelector(
        data.channels,
        intl["filter.category-type.channels.title"],
        CategoryType.CHANNEL,
    )

    private fun createSelector(
        options: List<CategoryDto>,
        title: String,
        categoryType: CategoryType,
    ): CategorySelector? = options
        .sortedBy { it.name }
        .map { CategoryOption(it.name, categoryType, it.link) }
        .toTypedArray()
        .takeIf { it.isNotEmpty() }
        ?.let { CategorySelector(title, categoryType, it) }
}
