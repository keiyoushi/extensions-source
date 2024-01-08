package eu.kanade.tachiyomi.multisrc.eromuse

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class EroMuseGenerator : ThemeSourceGenerator {

    override val themePkg = "eromuse"

    override val themeClass = "EroMuse"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("8Muses", "https://comics.8muses.com", "en", className = "EightMuses", isNsfw = true, overrideVersionCode = 1),
        SingleLang("Erofus", "https://www.erofus.com", "en", isNsfw = true, overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            EroMuseGenerator().createAll()
        }
    }
}
