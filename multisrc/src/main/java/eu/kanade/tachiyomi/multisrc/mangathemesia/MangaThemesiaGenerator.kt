package eu.kanade.tachiyomi.multisrc.mangathemesia

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

// Formerly WPMangaStream & WPMangaReader -> MangaThemesia
class MangaThemesiaGenerator : ThemeSourceGenerator {

    override val themePkg = "mangathemesia"

    override val themeClass = "MangaThemesia"

    override val baseVersionCode: Int = 27

    override val sources = listOf(
        SingleLang("Cartel de Manhwas", "https://carteldemanhwas.com", "es", overrideVersionCode = 5),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaThemesiaGenerator().createAll()
        }
    }
}
