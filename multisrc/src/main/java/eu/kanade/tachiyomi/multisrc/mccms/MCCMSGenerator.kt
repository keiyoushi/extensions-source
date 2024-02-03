package eu.kanade.tachiyomi.multisrc.mccms

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MCCMSGenerator : ThemeSourceGenerator {
    override val themeClass = "MCCMS"
    override val themePkg = "mccms"
    override val baseVersionCode = 6
    override val sources = listOf(
        SingleLang(
            name = "Kuaikuai Manhua 3",
            baseUrl = "https://mobile3.manhuaorg.com",
            lang = "zh",
            className = "Kuaikuai3",
            sourceName = "快快漫画3",
            overrideVersionCode = 0,
        ),
        SingleLang(
            name = "6Manhua",
            baseUrl = "https://www.liumanhua.com",
            lang = "zh",
            className = "SixMH",
            sourceName = "六漫画",
            overrideVersionCode = 4,
        ),
        SingleLang(
            name = "Miaoshang Manhua",
            baseUrl = "https://www.miaoshangmanhua.com",
            lang = "zh",
            className = "Miaoshang",
            sourceName = "喵上漫画",
            overrideVersionCode = 0,
        ),
        // The following sources are from https://www.yy123.cyou/
        SingleLang( // 103=他的那里, same as: www.hmanwang.com, www.lmmh.cc, www.999mh.net
            name = "Dida Manhua",
            baseUrl = "https://www.didamanhua.com/index.php",
            lang = "zh",
            isNsfw = true,
            className = "DidaManhua",
            sourceName = "嘀嗒漫画",
            overrideVersionCode = 1,
        ),
        SingleLang( // 103=青春男女(完结), same as: www.hanman.men
            name = "Damao Manhua",
            baseUrl = "https://www.hanman.cyou/index.php",
            lang = "zh",
            isNsfw = true,
            className = "DamaoManhua",
            sourceName = "大猫漫画",
            overrideVersionCode = 0,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MCCMSGenerator().createAll()
        }
    }
}
