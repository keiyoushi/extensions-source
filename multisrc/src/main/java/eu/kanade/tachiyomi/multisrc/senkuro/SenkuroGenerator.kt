package eu.kanade.tachiyomi.multisrc.senkuro

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class SenkuroGenerator : ThemeSourceGenerator {

    override val themePkg = "senkuro"

    override val themeClass = "Senkuro"

    override val baseVersionCode = 2

    override val sources = listOf(
        SingleLang("Senkuro", "https://senkuro.com", "ru", overrideVersionCode = 0),
        SingleLang("Senkognito", "https://senkognito.com", "ru", isNsfw = true, overrideVersionCode = 0),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SenkuroGenerator().createAll()
        }
    }
}
