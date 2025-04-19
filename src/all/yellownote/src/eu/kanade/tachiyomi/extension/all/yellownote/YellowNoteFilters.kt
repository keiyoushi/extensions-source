package eu.kanade.tachiyomi.extension.all.yellownote

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.Filter

class YellowNoteFilters {

    data class SortOption(val name: String, val urlPart: String = "") {
        override fun toString() = this.name
    }

    class SortSelector(name: String, private val vals: Array<SortOption>) :
        Filter.Select<SortOption>(name, vals) {
        fun toUriPart() = vals[state].urlPart
    }

    data class CategoryOption(val name: String, val uriPart: String) {
        override fun toString() = this.name
    }

    class CategorySelector(name: String, values: Array<CategoryOption>) : Filter.Select<CategoryOption>(name, values) {
        fun toUriPart() = values[state].uriPart
    }

    companion object {
        fun createSortSelector(intl: Intl) = SortSelector(
            intl["filter.sort.title"],
            arrayOf(
                SortOption(intl["filter.sort.option.last-update"]),
                SortOption(intl["filter.sort.option.popularity"], "sort-hot"),
                SortOption(intl["filter.sort.option.most-comments"], "sort-comment"),
                SortOption(intl["filter.sort.option.latest-comments"], "sort-recent"),
            ),
        )

        private fun themeCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.theme.xiuren-featured"], "photos/album-1"),
            CategoryOption(intl["filter.category.option.theme.large-scale"], "photos/album-2"),
            CategoryOption(intl["filter.category.option.theme.sex"], "photos/album-3"),
            CategoryOption(intl["filter.category.option.theme.exposure"], "photos/album-4"),
            CategoryOption(intl["filter.category.option.theme.cosplay"], "photos/album-5"),
            CategoryOption(intl["filter.category.option.theme.sex-toy"], "photos/album-6"),
            CategoryOption(intl["filter.category.option.theme.bondage"], "photos/album-7"),
            CategoryOption(intl["filter.category.option.theme.shaved-pussy"], "photos/album-8"),
            CategoryOption(intl["filter.category.option.theme.lesbian"], "photos/album-9"),
            CategoryOption(intl["filter.category.option.theme.with-original-photos"], "photos/album-10"),
            CategoryOption(intl["filter.category.option.theme.with-video"], "photos/album-11"),
            CategoryOption(intl["filter.category.option.theme.amateur"], "amateurs"),
        )

        private fun chinaStudiosCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.chinese-studios-pans"], "photos/series-6310ce9b90056"),
            CategoryOption(intl["filter.category.option.chinese-studios-wind-sings"], "photos/series-6666a7ac3ba9c"),
            CategoryOption(intl["filter.category.option.chinese-studios-xing-se"], "photos/series-64f44d99ce673"),
            CategoryOption(intl["filter.category.option.chinese-studios-huang-fu"], "photos/series-665f8bafab4bc"),
            CategoryOption(intl["filter.category.option.chinese-studios-other-studios"], "photos/series-665f7d787d681"),
            CategoryOption(intl["filter.category.option.chinese-studios-metcn"], "photos/series-5f1dcdeaee582"),
            CategoryOption(intl["filter.category.option.chinese-studios-litu"], "photos/series-5f1d784995865"),
            CategoryOption(intl["filter.category.option.chinese-studios-midnight-project"], "photos/series-638e5a60b1770"),
            CategoryOption(intl["filter.category.option.chinese-studios-pandora"], "photos/series-5f23c44cd66bd"),
            CategoryOption(intl["filter.category.option.chinese-studios-missleg"], "photos/series-5f2089564c6c2"),
            CategoryOption(intl["filter.category.option.chinese-studios-iss"], "photos/series-646c69b675f3d"),
            CategoryOption(intl["filter.category.option.chinese-studios-aiss"], "photos/series-5f15f389e993e"),
            CategoryOption(intl["filter.category.option.chinese-studios-au"], "photos/series-5f60b98248a81"),
            CategoryOption(intl["filter.category.option.chinese-studios-beijing-angel"], "photos/series-622c7f95220a4"),
            CategoryOption(intl["filter.category.option.chinese-studios-wuji-works"], "photos/series-619a92aa1fa7a"),
            CategoryOption(intl["filter.category.option.chinese-studios-pomelo"], "photos/series-676c3e9b90749"),
            CategoryOption(intl["filter.category.option.chinese-studios-sk-silk"], "photos/series-5f382ba894af4"),
            CategoryOption(intl["filter.category.option.chinese-studios-ddy"], "photos/series-5f15f727df393"),
            CategoryOption(intl["filter.category.option.chinese-studios-dongguan-vgirls"], "photos/series-5f22ea422221c"),
            CategoryOption(intl["filter.category.option.chinese-studios-youmei"], "photos/series-61b997728043b"),
        )

        private fun otherPhotosCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.other-photos-chinese-nude"], "photos/series-64be21c972ca4"),
            CategoryOption(intl["filter.category.option.other-photos-korean-nude"], "photos/series-64be22b4a0fa0"),
            CategoryOption(intl["filter.category.option.other-photos-taiwan-nude"], "photos/series-64be21ef4cc51"),
            CategoryOption(intl["filter.category.option.other-photos-other-regions"], "photos/series-64be239ce73d4"),
        )

        private fun xiuRenCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.xiuren-all"], "photos/series-6660093348354"),
            CategoryOption(intl["filter.category.option.xiuren-leaked"], "photos/series-66600a3a227ee"),
            CategoryOption(intl["filter.category.option.xiuren-huayang"], "photos/series-5fc4ce40386af"),
            CategoryOption(intl["filter.category.option.xiuren-mygirl"], "photos/series-5f1495dbda4de"),
            CategoryOption(intl["filter.category.option.xiuren-imiss"], "photos/series-5f71afc92d8ab"),
            CategoryOption(intl["filter.category.option.xiuren-miitao"], "photos/series-5f1dd5a7ebe9a"),
            CategoryOption(intl["filter.category.option.xiuren-feilin"], "photos/series-5f14a3105d3e8"),
            CategoryOption(intl["filter.category.option.xiuren-youwu"], "photos/series-60673bec9dd11"),
            CategoryOption(intl["filter.category.option.xiuren-wings"], "photos/series-63d435352808c"),
            CategoryOption(intl["filter.category.option.xiuren-ruisg"], "photos/series-61263de287e2f"),
        )

        private fun koreanStudiosCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.korean-studios-makemodel"], "photos/series-665f81885f103"),
            CategoryOption(intl["filter.category.option.korean-studios-pure-media"], "photos/series-6224e755e21f4"),
            CategoryOption(intl["filter.category.option.korean-studios-espacia-korea"], "photos/series-665a2385a2367"),
            CategoryOption(intl["filter.category.option.korean-studios-loozy"], "photos/series-62888afad416b"),
        )

        private fun japaneseStudiosCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.japanese-studios-graphis"], "photos/series-6450b47c9db0b"),
            CategoryOption(intl["filter.category.option.japanese-studios-kuni-scan"], "photos/series-66f9665804471"),
            CategoryOption(intl["filter.category.option.japanese-studios-weekly-post-digital-photo"], "photos/series-66e68b9c96ab0"),
            CategoryOption(intl["filter.category.option.japanese-studios-morning-sexy"], "photos/series-670d7142b3d88"),
            CategoryOption(intl["filter.category.option.japanese-studios-prestige"], "photos/series-670791f5f2f0f"),
            CategoryOption(intl["filter.category.option.japanese-studios-x-city"], "photos/series-66fb8cca706ae"),
            CategoryOption(intl["filter.category.option.japanese-studios-friday"], "photos/series-66659e2d94489"),
            CategoryOption(intl["filter.category.option.japanese-studios-super-pose-book"], "photos/series-62a0a15911f16"),
            CategoryOption(intl["filter.category.option.japanese-studios-urabon"], "photos/series-6692ea004cc75"),
            CategoryOption(intl["filter.category.option.japanese-studios-escape"], "photos/series-66603af933ec9"),
            CategoryOption(intl["filter.category.option.japanese-studios-flash"], "photos/series-672a2029d6a32"),
        )

        private fun taiwanStudiosCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.taiwan-studios-jvid"], "photos/series-637b2029d2347"),
            CategoryOption(intl["filter.category.option.taiwan-studios-fantasy-factory"], "photos/series-5f889afb37619"),
            CategoryOption(intl["filter.category.option.taiwan-studios-tpimage"], "photos/series-5f7a0a80d3d66"),
        )

        private fun otherCategoryOptions(intl: Intl) = listOf(
            CategoryOption(intl["filter.category.option.others-ai-photos"], "photos/series-6443d480eb757"),
        )

        fun createCategorySelector(intl: Intl) = CategorySelector(
            intl["filter.category.title"],
            listOf(
                themeCategoryOptions(intl),
                taiwanStudiosCategoryOptions(intl),
                chinaStudiosCategoryOptions(intl),
                otherCategoryOptions(intl),
                koreanStudiosCategoryOptions(intl),
                japaneseStudiosCategoryOptions(intl),
                otherPhotosCategoryOptions(intl),
                xiuRenCategoryOptions(intl),
            ).flatten().toTypedArray(),
        )
    }
}
