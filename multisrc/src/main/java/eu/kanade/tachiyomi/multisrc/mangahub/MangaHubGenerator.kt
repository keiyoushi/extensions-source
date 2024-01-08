package eu.kanade.tachiyomi.multisrc.mangahub

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaHubGenerator : ThemeSourceGenerator {

    override val themePkg = "mangahub"

    override val themeClass = "MangaHub"

    override val baseVersionCode: Int = 22

    override val sources = listOf(
//        SingleLang("1Manga.co", "https://1manga.co", "en", isNsfw = true, className = "OneMangaCo"),
//        SingleLang("MangaFox.fun", "https://mangafox.fun", "en", isNsfw = true, className = "MangaFoxFun"),
//        SingleLang("MangaHere.onl", "https://mangahere.onl", "en", isNsfw = true, className = "MangaHereOnl"),
        SingleLang("MangaHub", "https://mangahub.io", "en", isNsfw = true, overrideVersionCode = 10, className = "MangaHubIo"),
//        SingleLang("Mangakakalot.fun", "https://mangakakalot.fun", "en", isNsfw = true, className = "MangakakalotFun"),
//        SingleLang("MangaNel", "https://manganel.me", "en", isNsfw = true),
//        SingleLang("MangaOnline.fun", "https://mangaonline.fun", "en", isNsfw = true, className = "MangaOnlineFun"),
        SingleLang("MangaPanda.onl", "https://mangapanda.onl", "en", className = "MangaPandaOnl"),
        SingleLang("MangaReader.site", "https://mangareader.site", "en", className = "MangaReaderSite"),
//        SingleLang("MangaToday", "https://mangatoday.fun", "en", isNsfw = true),
//        SingleLang("MangaTown (unoriginal)", "https://manga.town", "en", isNsfw = true, className = "MangaTownHub"),
        // SingleLang("MF Read Online", "https://mangafreereadonline.com", "en", isNsfw = true), // different pageListParse logic
        // SingleLang("OneManga.info", "https://onemanga.info", "en", isNsfw = true, className = "OneMangaInfo"), // Some chapters link to 1manga.co, hard to filter
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaHubGenerator().createAll()
        }
    }
}
