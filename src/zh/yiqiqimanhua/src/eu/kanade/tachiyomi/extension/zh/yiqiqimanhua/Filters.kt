package eu.kanade.tachiyomi.extension.zh.yiqiqimanhua

import eu.kanade.tachiyomi.source.model.Filter

class Category(val name: String, val id: Int, val path: String)

val CATEGORIES = listOf(
    Category("中文成人H漫畫", 30, "html/category/tt"),
    Category("日文成人H漫畫", 24, "html/category/jj"),
    Category("全彩CG", 32, "html/category/cg"),
    Category("全彩CG(中文全彩)", 36, "html/category/cg/cg-cn"),
    Category("全彩CG(日文全彩)", 37, "html/category/cg/cg-jp"),
    Category("全彩CG(純圖無字)", 35, "html/category/cg/cg-no"),
)

class CategoryFilter : Filter.Select<String>("分類", arrayOf("全部") + CATEGORIES.map { it.name }) {
    val selected get() = CATEGORIES.getOrNull(state - 1)
}
