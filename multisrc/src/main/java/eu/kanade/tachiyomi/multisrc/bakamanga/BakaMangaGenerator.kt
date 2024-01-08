package eu.kanade.tachiyomi.multisrc.bakamanga

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class BakaMangaGenerator : ThemeSourceGenerator {
    override val themePkg = "bakamanga"

    override val themeClass = "BakaManga"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("Manhwa XXL", "https://manhwaxxl.com", "en", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = BakaMangaGenerator().createAll()
    }
}
