package eu.kanade.tachiyomi.multisrc.paprika

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PaprikaAltGenerator : ThemeSourceGenerator {

    override val themePkg = "paprika"

    override val themeClass = "PaprikaAlt"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("MangaReader.cc", "http://mangareader.cc/", "en", className = "MangaReaderCC", overrideVersionCode = 1), // more sites in the future might use MangaReader.cc 's overrides as they did in the past
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PaprikaAltGenerator().createAll()
        }
    }
}
