package eu.kanade.tachiyomi.multisrc.po2scans

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PO2ScansGenerator : ThemeSourceGenerator {
    override val themePkg = "po2scans"

    override val themeClass = "PO2Scans"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("A Pair Of 2+", "https://po2scans.com", "en", className = "APairOf2", overrideVersionCode = 31),
        SingleLang("Sadscans", "https://sadscans.com", "tr"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PO2ScansGenerator().createAll()
        }
    }
}
