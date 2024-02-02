package eu.kanade.tachiyomi.multisrc.kemono

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class KemonoGenerator : ThemeSourceGenerator {

    override val themeClass = "Kemono"

    override val themePkg = "kemono"

    override val baseVersionCode = 9

    override val sources = listOf(
        SingleLang("Kemono", "https://kemono.su", "all", isNsfw = true),
        SingleLang("Coomer", "https://coomer.su", "all", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            KemonoGenerator().createAll()
        }
    }
}
