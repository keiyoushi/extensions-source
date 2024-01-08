package eu.kanade.tachiyomi.multisrc.bakkin

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class BakkinGenerator : ThemeSourceGenerator {
    override val themePkg = "bakkin"

    override val themeClass = "BakkinReaderX"

    override val baseVersionCode = 6

    override val sources = listOf(
        SingleLang("Bakkin", "https://bakkin.moe/reader/", "en"),
        SingleLang("Bakkin Self-hosted", "", "en", className = "BakkinSelfHosted"),
        SingleLang("UltraLight Scans", "https://readlight.org/", "en", className = "UltraLightScans"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = BakkinGenerator().createAll()
    }
}
