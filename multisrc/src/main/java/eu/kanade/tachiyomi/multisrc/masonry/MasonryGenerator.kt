package eu.kanade.tachiyomi.multisrc.masonry

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MasonryGenerator : ThemeSourceGenerator {

    override val themePkg = "masonry"

    override val themeClass = "Masonry"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("Elite Babes", "https://www.elitebabes.com", "all", isNsfw = true),
        SingleLang("Femjoy Hunter", "https://www.femjoyhunter.com", "all", isNsfw = true),
        SingleLang("FTV Hunter", "https://www.ftvhunter.com", "all", isNsfw = true),
        SingleLang("Joymii Hub", "https://www.joymiihub.com", "all", isNsfw = true),
        SingleLang("Metart Hunter", "https://www.metarthunter.com", "all", isNsfw = true),
        SingleLang("Playmate Hunter", "https://pmatehunter.com", "all", isNsfw = true),
        SingleLang("XArt Hunter", "https://www.xarthunter.com", "all", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MasonryGenerator().createAll()
        }
    }
}
