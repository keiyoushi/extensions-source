package eu.kanade.tachiyomi.multisrc.sinmh

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class SinMHGenerator : ThemeSourceGenerator {
    override val themeClass = "SinMH"
    override val themePkg = "sinmh"
    override val baseVersionCode = 10
    override val sources = listOf(
        SingleLang(
            name = "Gufeng Manhua",
            baseUrl = "https://www.gufengmh.com",
            lang = "zh",
            className = "Gufengmh",
            sourceName = "古风漫画网",
            overrideVersionCode = 6,
        ),
        SingleLang(
            name = "Imitui Manhua",
            baseUrl = "https://www.imitui.com",
            lang = "zh",
            className = "Imitui",
            sourceName = "爱米推漫画",
            overrideVersionCode = 3,
        ),
        SingleLang( // This site blocks IP outside China
            name = "YKMH",
            baseUrl = "http://www.ykmh.com",
            lang = "zh",
            className = "YKMH",
            pkgName = "manhuadui",
            sourceName = "优酷漫画",
            overrideVersionCode = 17,
        ),
        SingleLang(
            name = "Qinqin Manhua",
            baseUrl = "https://www.acgud.com",
            lang = "zh",
            className = "Qinqin",
            sourceName = "亲亲漫画",
            overrideVersionCode = 2,
        ),
        SingleLang(
            name = "57Manhua",
            baseUrl = "http://www.wuqimh.net",
            lang = "zh",
            className = "WuqiManga",
            sourceName = "57漫画",
            overrideVersionCode = 5,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SinMHGenerator().createAll()
        }
    }
}
