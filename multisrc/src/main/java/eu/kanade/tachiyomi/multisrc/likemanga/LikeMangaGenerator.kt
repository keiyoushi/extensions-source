package eu.kanade.tachiyomi.multisrc.likemanga

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class LikeMangaGenerator : ThemeSourceGenerator {

    override val themePkg = "likemanga"

    override val themeClass = "LikeManga"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("LikeManga", "https://likemanga.io", "en", className = "LikeMangaIO", pkgName = "likemanga", overrideVersionCode = 3),
        SingleLang("ZinManga.io", "https://zinmanga.io", "en", className = "ZinMangaIO", pkgName = "zinmanhwa", overrideVersionCode = 34),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            LikeMangaGenerator().createAll()
        }
    }
}
