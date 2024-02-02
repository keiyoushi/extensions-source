package eu.kanade.tachiyomi.multisrc.colorlibanime

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ColorlibAnimeGenerator : ThemeSourceGenerator {

    override val themePkg = "colorlibanime"

    override val themeClass = "ColorlibAnime"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Sekte Komik", "https://sektekomik.xyz", "id", overrideVersionCode = 26),
        SingleLang("Komikzoid", "https://komikzoid.id", "id"),
        SingleLang("Neumanga", "https://neumanga.xyz", "id", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ColorlibAnimeGenerator().createAll()
        }
    }
}
