package eu.kanade.tachiyomi.multisrc.lectortmo

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class LectorTmoGenerator : ThemeSourceGenerator {

    override val themePkg = "lectortmo"

    override val themeClass = "LectorTmo"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("LectorManga", "https://lectormanga.com", "es", isNsfw = true, overrideVersionCode = 34),
        SingleLang("TuMangaOnline", "https://visortmo.com", "es", isNsfw = true, overrideVersionCode = 49),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            LectorTmoGenerator().createAll()
        }
    }
}
