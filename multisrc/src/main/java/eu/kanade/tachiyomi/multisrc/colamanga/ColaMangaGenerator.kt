package eu.kanade.tachiyomi.multisrc.colamanga

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ColaMangaGenerator : ThemeSourceGenerator {

    override val themePkg = "colamanga"

    override val themeClass = "ColaManga"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("COLAMANGA", "https://www.colamanga.com", "zh", overrideVersionCode = 15, className = "Onemanhua"),
        SingleLang("MangaDig", "https://mangadig.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ColaMangaGenerator().createAll()
        }
    }
}
