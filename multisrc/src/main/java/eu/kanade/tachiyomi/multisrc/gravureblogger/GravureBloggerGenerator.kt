package eu.kanade.tachiyomi.multisrc.gravureblogger

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class GravureBloggerGenerator : ThemeSourceGenerator {

    override val themePkg = "gravureblogger"

    override val themeClass = "GravureBlogger"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang(
            name = "Idol. gravureprincess .date",
            baseUrl = "https://idol.gravureprincess.date",
            lang = "ja",
            isNsfw = true,
            className = "IdolGravureprincessDate",
        ),
        SingleLang(
            name = "MIC MIC IDOL",
            baseUrl = "https://www.micmicidol.club",
            lang = "ja",
            isNsfw = true,
            className = "MicMicIdol",
            overrideVersionCode = 1,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GravureBloggerGenerator().createAll()
        }
    }
}
