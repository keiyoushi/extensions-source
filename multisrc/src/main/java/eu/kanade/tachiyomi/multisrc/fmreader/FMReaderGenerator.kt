package eu.kanade.tachiyomi.multisrc.fmreader

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FMReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "fmreader"

    override val themeClass = "FMReader"

    override val baseVersionCode: Int = 8

    override val sources = listOf(
        SingleLang("KissLove", "https://klz9.com", "ja", isNsfw = true, overrideVersionCode = 4),
        SingleLang("Manga-TR", "https://manga-tr.com", "tr", className = "MangaTR", overrideVersionCode = 3),
        SingleLang("Say Truyen", "https://saytruyenvip.com", "vi", overrideVersionCode = 3),
        SingleLang("WeLoveManga", "https://weloma.art", "ja", pkgName = "rawlh", isNsfw = true, overrideVersionCode = 5),
        SingleLang("Manga1000", "https://manga1000.top", "ja"),
        SingleLang("WeLoveMangaOne", "https://welovemanga.one", "ja", isNsfw = true, overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FMReaderGenerator().createAll()
        }
    }
}
