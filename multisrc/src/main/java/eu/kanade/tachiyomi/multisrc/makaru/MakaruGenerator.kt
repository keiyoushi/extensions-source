package eu.kanade.tachiyomi.multisrc.makaru

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MakaruGenerator : ThemeSourceGenerator {

    override val themePkg = "makaru"

    override val themeClass = "Makaru"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("KomikGes", "https://www.komikges.my.id", "id"),
        SingleLang("ReYume", "https://www.re-yume.my.id", "id", pkgName = "inazumanga", overrideVersionCode = 34),
        SingleLang("YuraManga", "https://www.yuramanga.my.id", "id"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MakaruGenerator().createAll()
        }
    }
}
