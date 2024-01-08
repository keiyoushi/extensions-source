package eu.kanade.tachiyomi.multisrc.zbulu

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ZbuluGenerator : ThemeSourceGenerator {

    override val themePkg = "zbulu"

    override val themeClass = "Zbulu"

    override val baseVersionCode: Int = 6

    override val sources = listOf(
        SingleLang("HolyManga", "https://w30.holymanga.net", "en", isNsfw = true, overrideVersionCode = 2),
        SingleLang("My Toon", "https://mytoon.net", "en", isNsfw = true, overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ZbuluGenerator().createAll()
        }
    }
}
