package eu.kanade.tachiyomi.multisrc.sinmh

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class SinMHGenerator : ThemeSourceGenerator {
    override val themeClass = "SinMH"
    override val themePkg = "sinmh"
    override val baseVersionCode = 11
    override val sources = listOf(
        SingleLang(
            name = "Gufeng Manhua",
            baseUrl = "https://www.gufengmh.com",
            lang = "zh",
            className = "Gufengmh",
            sourceName = "古风漫画网",
            overrideVersionCode = 6,
        ),
        SingleLang( // This site blocks IP outside China
            name = "YKMH",
            baseUrl = "http://www.ykmh.com",
            lang = "zh",
            isNsfw = true,
            className = "YKMH",
            pkgName = "manhuadui",
            sourceName = "优酷漫画",
            overrideVersionCode = 17,
        ),
        SingleLang(
            name = "Qinqin Manhua",
            baseUrl = "http://www.acgwd.com",
            lang = "zh",
            className = "Qinqin",
            sourceName = "亲亲漫画",
            overrideVersionCode = 2,
        ),
        SingleLang(
            name = "92Manhua",
            baseUrl = "http://www.92mh.com",
            lang = "zh",
            isNsfw = true,
            className = "JiuerManhua",
            sourceName = "92漫画",
            overrideVersionCode = 0,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SinMHGenerator().createAll()
        }
    }
}
