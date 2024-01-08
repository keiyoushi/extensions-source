package eu.kanade.tachiyomi.multisrc.paprika

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PaprikaGenerator : ThemeSourceGenerator {

    override val themePkg = "paprika"

    override val themeClass = "Paprika"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("MangaNelos.com", "http://manganelos.com", "en", className = "MangaNelosCom", overrideVersionCode = 1),
        SingleLang("MangaHere.today", "http://mangahere.today", "en", className = "MangaHereToday"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PaprikaGenerator().createAll()
        }
    }
}
