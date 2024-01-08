package eu.kanade.tachiyomi.multisrc.monochrome

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MonochromeGenerator : ThemeSourceGenerator {
    override val themePkg = "monochrome"

    override val themeClass = "MonochromeCMS"

    override val baseVersionCode = 4

    override val sources = listOf(
        SingleLang("Monochrome Scans", "https://manga.d34d.one", "en"),
        SingleLang("Monochrome Custom", "", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = MonochromeGenerator().createAll()
    }
}
