package eu.kanade.tachiyomi.multisrc.flixscans

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FlixScansGenerator : ThemeSourceGenerator {

    override val themePkg = "flixscans"

    override val themeClass = "FlixScans"

    override val baseVersionCode: Int = 3

    override val sources = listOf(
        SingleLang("Flix Scans", "https://flixscans.org", "en", className = "FlixScansNet", pkgName = "flixscans"),
        SingleLang("جالاكسي مانجا", "https://flixscans.com", "ar", className = "GalaxyManga", overrideVersionCode = 26),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FlixScansGenerator().createAll()
        }
    }
}
