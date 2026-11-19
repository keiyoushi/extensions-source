package eu.kanade.tachiyomi.extension.all.yellownote

import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.lib.i18n.Intl

class SortOption(val name: String, val urlPart: String = "") {
    override fun toString() = this.name
}

class SortSelector(name: String, private val vals: Array<SortOption>) : Filter.Select<SortOption>(name, vals) {
    fun toUriPart() = vals[state].urlPart
}

class CategoryOption(val name: String, val uriPart: String) {
    override fun toString() = this.name
}

class CategorySelector(name: String, values: Array<CategoryOption>) : Filter.Select<CategoryOption>(name, values) {
    fun toUriPart() = values[state].uriPart
}

object Filters {

    fun createSortSelector(intl: Intl) = SortSelector(
        intl["filter.sort.title"],
        arrayOf(
            SortOption(intl["filter.sort.option.last-update"]),
            SortOption(intl["filter.sort.option.popularity"], "hot"),
            SortOption(intl["filter.sort.option.most-comments"], "comment"),
            SortOption(intl["filter.sort.option.latest-comments"], "recent"),
        ),
    )

    private fun themeCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.theme.xiuren-featured"], "photo-album/1"),
        CategoryOption(intl["filter.category.option.theme.large-scale"], "photo-album/2"),
        CategoryOption(intl["filter.category.option.theme.sex"], "photo-album/3"),
        CategoryOption(intl["filter.category.option.theme.exposure"], "photo-album/4"),
        CategoryOption(intl["filter.category.option.theme.cosplay"], "photo-album/5"),
        CategoryOption(intl["filter.category.option.theme.sex-toy"], "photo-album/6"),
        CategoryOption(intl["filter.category.option.theme.bondage"], "photo-album/7"),
        CategoryOption(intl["filter.category.option.theme.shaved-pussy"], "photo-album/8"),
        CategoryOption(intl["filter.category.option.theme.lesbian"], "photo-album/9"),
        CategoryOption(intl["filter.category.option.theme.with-original-photos"], "photo-album/10"),
        CategoryOption(intl["filter.category.option.theme.with-video"], "photo-album/11"),
        CategoryOption(intl["filter.category.option.theme.amateur"], "amateurs"),
    )

    private fun chinaStudiosCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.chinese-studios-pans"], "photo-series/6310ce9b90056"),
        CategoryOption(intl["filter.category.option.chinese-studios-wind-sings"], "photo-series/6666a7ac3ba9c"),
        CategoryOption(intl["filter.category.option.chinese-studios-xing-se"], "photo-series/64f44d99ce673"),
        CategoryOption(intl["filter.category.option.chinese-studios-huang-fu"], "photo-series/665f8bafab4bc"),
        CategoryOption(intl["filter.category.option.chinese-studios-other-studios"], "photo-series/665f7d787d681"),
        CategoryOption(intl["filter.category.option.chinese-studios-metcn"], "photo-series/5f1dcdeaee582"),
        CategoryOption(intl["filter.category.option.chinese-studios-litu"], "photo-series/5f1d784995865"),
        CategoryOption(intl["filter.category.option.chinese-studios-midnight-project"], "photo-series/638e5a60b1770"),
        CategoryOption(intl["filter.category.option.chinese-studios-pandora"], "photo-series/5f23c44cd66bd"),
        CategoryOption(intl["filter.category.option.chinese-studios-missleg"], "photo-series/5f2089564c6c2"),
        CategoryOption(intl["filter.category.option.chinese-studios-iss"], "photo-series/646c69b675f3d"),
        CategoryOption(intl["filter.category.option.chinese-studios-aiss"], "photo-series/5f15f389e993e"),
        CategoryOption(intl["filter.category.option.chinese-studios-au"], "photo-series/5f60b98248a81"),
        CategoryOption(intl["filter.category.option.chinese-studios-beijing-angel"], "photo-series/622c7f95220a4"),
        CategoryOption(intl["filter.category.option.chinese-studios-wuji-works"], "photo-series/619a92aa1fa7a"),
        CategoryOption(intl["filter.category.option.chinese-studios-pomelo"], "photo-series/676c3e9b90749"),
        CategoryOption(intl["filter.category.option.chinese-studios-sk-silk"], "photo-series/5f382ba894af4"),
        CategoryOption(intl["filter.category.option.chinese-studios-ddy"], "photo-series/5f15f727df393"),
        CategoryOption(intl["filter.category.option.chinese-studios-dongguan-vgirls"], "photo-series/5f22ea422221c"),
        CategoryOption(intl["filter.category.option.chinese-studios-youmei"], "photo-series/61b997728043b"),
    )

    private fun otherPhotosCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.other-photos-chinese-nude"], "photo-series/64be21c972ca4"),
        CategoryOption(intl["filter.category.option.other-photos-korean-nude"], "photo-series/64be22b4a0fa0"),
        CategoryOption(intl["filter.category.option.other-photos-taiwan-nude"], "photo-series/64be21ef4cc51"),
        CategoryOption(intl["filter.category.option.other-photos-other-regions"], "photo-series/64be239ce73d4"),
    )

    private fun xiuRenCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.xiuren-all"], "photo-series/6660093348354"),
        CategoryOption(intl["filter.category.option.xiuren-leaked"], "photo-series/66600a3a227ee"),
        CategoryOption(intl["filter.category.option.xiuren-huayang"], "photo-series/5fc4ce40386af"),
        CategoryOption(intl["filter.category.option.xiuren-mygirl"], "photo-series/5f1495dbda4de"),
        CategoryOption(intl["filter.category.option.xiuren-imiss"], "photo-series/5f71afc92d8ab"),
        CategoryOption(intl["filter.category.option.xiuren-miitao"], "photo-series/5f1dd5a7ebe9a"),
        CategoryOption(intl["filter.category.option.xiuren-feilin"], "photo-series/5f14a3105d3e8"),
        CategoryOption(intl["filter.category.option.xiuren-youwu"], "photo-series/60673bec9dd11"),
        CategoryOption(intl["filter.category.option.xiuren-wings"], "photo-series/63d435352808c"),
        CategoryOption(intl["filter.category.option.xiuren-ruisg"], "photo-series/61263de287e2f"),
    )

    private fun koreanStudiosCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.korean-studios-makemodel"], "photo-series/665f81885f103"),
        CategoryOption(intl["filter.category.option.korean-studios-pure-media"], "photo-series/6224e755e21f4"),
        CategoryOption(intl["filter.category.option.korean-studios-espacia-korea"], "photo-series/665a2385a2367"),
        CategoryOption(intl["filter.category.option.korean-studios-loozy"], "photo-series/62888afad416b"),
    )

    private fun japaneseStudiosCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.japanese-studios-graphis"], "photo-series/6450b47c9db0b"),
        CategoryOption(intl["filter.category.option.japanese-studios-weekly-post-digital-photo"], "photo-series/66e68b9c96ab0"),
        CategoryOption(intl["filter.category.option.japanese-studios-morning-sexy"], "photo-series/670d7142b3d88"),
        CategoryOption(intl["filter.category.option.japanese-studios-prestige"], "photo-series/670791f5f2f0f"),
        CategoryOption(intl["filter.category.option.japanese-studios-x-city"], "photo-series/66fb8cca706ae"),
        CategoryOption(intl["filter.category.option.japanese-studios-friday"], "photo-series/66659e2d94489"),
        CategoryOption(intl["filter.category.option.japanese-studios-super-pose-book"], "photo-series/62a0a15911f16"),
        CategoryOption(intl["filter.category.option.japanese-studios-urabon"], "photo-series/6692ea004cc75"),
        CategoryOption(intl["filter.category.option.japanese-studios-escape"], "photo-series/66603af933ec9"),
        CategoryOption(intl["filter.category.option.japanese-studios-flash"], "photo-series/672a2029d6a32"),
    )

    private fun taiwanStudiosCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.taiwan-studios-jvid"], "photo-series/637b2029d2347"),
        CategoryOption(intl["filter.category.option.taiwan-studios-fantasy-factory"], "photo-series/5f889afb37619"),
        CategoryOption(intl["filter.category.option.taiwan-studios-tpimage"], "photo-series/5f7a0a80d3d66"),
    )

    private fun otherCategoryOptions(intl: Intl) = listOf(
        CategoryOption(intl["filter.category.option.others-ai-photos"], "photo-series/6443d480eb757"),
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
