package eu.kanade.tachiyomi.multisrc.nepnep

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class NepNepGenerator : ThemeSourceGenerator {

    override val themePkg = "nepnep"

    override val themeClass = "NepNep"

    override val baseVersionCode: Int = 11

    override val sources = listOf(
        SingleLang("MangaSee", "https://mangasee123.com", "en", overrideVersionCode = 24),
        SingleLang("MangaLife", "https://manga4life.com", "en", overrideVersionCode = 16),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            NepNepGenerator().createAll()
        }
    }
}
