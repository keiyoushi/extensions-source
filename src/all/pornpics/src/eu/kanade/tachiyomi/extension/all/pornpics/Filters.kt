package eu.kanade.tachiyomi.extension.all.pornpics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.lib.i18n.Intl
import java.util.Locale

data class SortOption(val name: String, val urlPart: String? = null) {
    override fun toString() = this.name
}

class SortSelector(name: String, private val vals: Array<SortOption>) : Filter.Select<SortOption>(name, vals) {
    fun toUriPart() = vals[state].urlPart
}

enum class CategoryType {
    RECOMMEND,
    CATEGORY,
    TAG,
    PORN_STAR,
    CHANNEL,
    ;

    companion object {
        private val mNameToCategoryType = CategoryType.values().associateBy { it.name.lowercase(Locale.US) }

        fun of(name: String) = mNameToCategoryType[name.lowercase(Locale.US)]!!
    }
}

data class CategoryOption(val name: String, val type: CategoryType, val urlPart: String) {
    override fun toString() = this.name
    fun toUrlPart() = urlPart
    fun useSearch() = urlPart.contains("/search/srch.php?")
}

data class ActiveCategoryOption(val name: String, val categoryType: CategoryType?) {
    override fun toString() = this.name
}

class ActiveCategoryTypeSelector(name: String, values: Array<ActiveCategoryOption>) : Filter.Select<ActiveCategoryOption>(name, values) {
    fun selected() = values[state].categoryType!!
    fun selectedCategoryOption(filters: FilterList): CategoryOption {
        val selectors = filters.filterIsInstance<CategorySelector>()
        val selected = selected()
        return selectors[selected.ordinal].selected()
    }
}

class CategorySelector(name: String, values: Array<CategoryOption>) : Filter.Select<CategoryOption>(name, values) {
    fun selected() = values[state]
}

fun createSortSelector(intl: Intl) = SortSelector(
    intl["filter.time.title"],
    arrayOf(
        SortOption(intl["filter.time.option.popular"]),
        SortOption(intl["filter.time.option.recent"], "recent?date=latest"),
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
        ?.let { CategorySelector(title, it) }
}
