package eu.kanade.tachiyomi.multisrc.fmreader

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FMReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "fmreader"

    override val themeClass = "FMReader"

    override val baseVersionCode: Int = 9

    override val sources = listOf(
        SingleLang("KissLove", "https://klz9.com", "ja", isNsfw = true, overrideVersionCode = 5),
        SingleLang("Manga-TR", "https://manga-tr.com", "tr", className = "MangaTR", overrideVersionCode = 3),
        SingleLang("Manga1000", "https://manga1000.top", "ja", overrideVersionCode = 2),
        SingleLang("Nicomanga", "https://nicomanga.com", "ja", isNsfw = true),
        SingleLang("WeLoveManga", "https://weloma.art", "ja", pkgName = "rawlh", isNsfw = true, overrideVersionCode = 5),
        SingleLang("WeLoveMangaOne", "https://welovemanga.one", "ja", isNsfw = true, overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FMReaderGenerator().createAll()
        }
    }
}
