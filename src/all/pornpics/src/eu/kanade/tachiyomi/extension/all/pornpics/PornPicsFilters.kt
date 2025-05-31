package eu.kanade.tachiyomi.extension.all.pornpics

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable
import java.util.Locale

class PornPicsFilters {

    data class SortOption(val name: String, val urlPart: String? = null) {
        override fun toString() = this.name
    }

    class SortSelector(name: String, private val vals: Array<SortOption>) :
        Filter.Select<SortOption>(name, vals) {
        fun toUriPart() = vals[state].urlPart
    }

    enum class CategoryType() {
        RECOMMEND,
        CATEGORY,
        TAG,
        PORN_STAR,
        CHANNEL,
        ;

        companion object {
            val mNameToCategoryType = CategoryType.values().associateBy { it.name.lowercase(Locale.US) }

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

    class ActiveCategoryTypeSelector(name: String, values: Array<ActiveCategoryOption>) :
        Filter.Select<ActiveCategoryOption>(name, values) {
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

    companion object {
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
                ActiveCategoryOption(intl["filter.active-category-type.option.recommend"], CategoryType.RECOMMEND),
                ActiveCategoryOption(intl["filter.active-category-type.option.categories"], CategoryType.CATEGORY),
                ActiveCategoryOption(intl["filter.active-category-type.option.tags"], CategoryType.TAG),
                ActiveCategoryOption(intl["filter.active-category-type.option.porn-star"], CategoryType.PORN_STAR),
                ActiveCategoryOption(intl["filter.active-category-type.option.channels"], CategoryType.CHANNEL),
            ),
        )

        @Serializable
        data class RecommendCategoryDto(val name: String, val categoryTypeName: String, val link: String)

        fun createRecommendSelector(intl: Intl): CategorySelector {
            val options = JsonFileLoader.loadLangJsonAs<List<RecommendCategoryDto>>(
                "recommend_categories",
                intl.chosenLanguage,
            )
                .map { CategoryOption(it.name, CategoryType.of(it.categoryTypeName), it.link) }
                .sortedBy { it.name }
                .toTypedArray()
            return CategorySelector(
                intl["filter.category-type.recommend.title"],
                options,
            )
        }

        @Serializable
        data class CategoryDto(val name: String, val link: String)

        fun createCategorySelector(intl: Intl): CategorySelector {
            val options = JsonFileLoader.loadLangJsonAs<List<CategoryDto>>("categories", intl.chosenLanguage)
                .map { CategoryOption(it.name, CategoryType.CATEGORY, it.link) }
                .sortedBy { it.name }
                .toTypedArray()

            return CategorySelector(
                intl["filter.category-type.categories.title"],
                options,
            )
        }

        fun createTagSelector(intl: Intl): CategorySelector {
            val options = JsonFileLoader.loadLangJsonAs<List<CategoryDto>>("tags", intl.chosenLanguage)
                .map { CategoryOption(it.name, CategoryType.TAG, it.link) }
                .sortedBy { it.name }
                .toTypedArray()

            return CategorySelector(
                intl["filter.category-type.tags.title"],
                options,
            )
        }

        fun createPornStarSelector(intl: Intl): CategorySelector {
            val options = JsonFileLoader.loadLangJsonAs<List<CategoryDto>>("porn_stars", intl.chosenLanguage)
                .map { CategoryOption(it.name, CategoryType.PORN_STAR, it.link) }
                .sortedBy { it.name }
                .toTypedArray()
            return CategorySelector(
                intl["filter.category-type.porn-star.title"],
                options,
            )
        }

        fun createChannelSelector(intl: Intl): CategorySelector {
            val options = JsonFileLoader.loadLangJsonAs<List<CategoryDto>>("channels", intl.chosenLanguage)
                .map { CategoryOption(it.name, CategoryType.CHANNEL, it.link) }
                .sortedBy { it.name }
                .toTypedArray()
            return CategorySelector(
                intl["filter.category-type.channels.title"],
                options,
            )
        }
    }
}
