package eu.kanade.tachiyomi.multisrc.madtheme

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MadThemeGenerator : ThemeSourceGenerator {

    override val themePkg = "madtheme"

    override val themeClass = "MadTheme"

    override val baseVersionCode: Int = 10

    override val sources = listOf(
        SingleLang("MangaFab", "https://mangafab.com", "en", isNsfw = true),
        SingleLang("MangaSaga", "https://mangasaga.com", "en", isNsfw = true),
        SingleLang("MangaSpin", "https://mangaspin.com", "en", isNsfw = true),
        SingleLang("ManhuaNow", "https://manhuanow.com", "en", isNsfw = true),
        SingleLang("ManhuaSite", "https://manhuasite.com", "en", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MadThemeGenerator().createAll()
        }
    }
}
