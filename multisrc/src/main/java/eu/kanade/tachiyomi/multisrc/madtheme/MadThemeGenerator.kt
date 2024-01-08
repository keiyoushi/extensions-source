package eu.kanade.tachiyomi.multisrc.madtheme

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MadThemeGenerator : ThemeSourceGenerator {

    override val themePkg = "madtheme"

    override val themeClass = "MadTheme"

    override val baseVersionCode: Int = 13

    override val sources = listOf(
        SingleLang("BeeHentai", "https://beehentai.com", "en", isNsfw = true),
        SingleLang("MangaBuddy", "https://mangabuddy.com", "en", isNsfw = true, overrideVersionCode = 2),
        SingleLang("MangaCute", "https://mangacute.com", "en", isNsfw = true),
        SingleLang("MangaForest", "https://mangaforest.me", "en", isNsfw = true, overrideVersionCode = 1),
        SingleLang("MangaPuma", "https://mangapuma.com", "en", isNsfw = true, overrideVersionCode = 2),
        SingleLang("MangaXYZ", "https://mangaxyz.com", "en", isNsfw = true),
        SingleLang("Toonily.me", "https://toonily.me", "en", isNsfw = true, className = "ToonilyMe"),
        SingleLang("TooniTube", "https://toonitube.com", "en", isNsfw = true),
        SingleLang("TrueManga", "https://truemanga.com", "en", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MadThemeGenerator().createAll()
        }
    }
}
