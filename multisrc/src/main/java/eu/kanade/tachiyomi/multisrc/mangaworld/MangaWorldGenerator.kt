package eu.kanade.tachiyomi.multisrc.mangaworld

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaWorldGenerator : ThemeSourceGenerator {

    override val themePkg = "mangaworld"
    override val themeClass = "MangaWorld"
    override val baseVersionCode: Int = 2

    override val sources = listOf(
        SingleLang("Mangaworld", "https://www.mangaworld.so", "it", pkgName = "mangaworld", overrideVersionCode = 5),
        SingleLang("MangaworldAdult", "https://www.mangaworldadult.com", "it", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaWorldGenerator().createAll()
        }
    }
}
