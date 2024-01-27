package eu.kanade.tachiyomi.multisrc.blogtruyen

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class BlogTruyenGenerator : ThemeSourceGenerator {

    override val themePkg = "blogtruyen"

    override val themeClass = "BlogTruyen"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("BlogTruyen", "https://blogtruyenmoi.com", "vi", className = "BlogTruyenMoi", pkgName = "blogtruyen", overrideVersionCode = 17),
        SingleLang("BlogTruyen.vn (unoriginal)", "https://blogtruyenvn.com", "vi", className = "BlogTruyenVn"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BlogTruyenGenerator().createAll()
        }
    }
}
