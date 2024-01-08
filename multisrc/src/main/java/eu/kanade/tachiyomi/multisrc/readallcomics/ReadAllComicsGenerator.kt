package eu.kanade.tachiyomi.multisrc.readallcomics

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ReadAllComicsGenerator : ThemeSourceGenerator {

    override val themePkg = "readallcomics"

    override val themeClass = "ReadAllComics"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("ReadAllComics", "https://readallcomics.com", "en", className = "ReadAllComicsCom", overrideVersionCode = 1),
        SingleLang("ReadAllManga", "https://readallmanga.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ReadAllComicsGenerator().createAll()
        }
    }
}
