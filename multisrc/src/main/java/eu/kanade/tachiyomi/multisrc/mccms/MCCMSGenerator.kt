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
            name = "Manhuawu",
            baseUrl = "https://www.mhua5.com",
            lang = "zh",
            className = "Manhuawu",
            sourceName = "漫画屋",
            overrideVersionCode = 0,
        ),
        // The following sources are from https://www.yy123.cyou/ and are configured to use MCCMSNsfw
        SingleLang( // 103=校园梦精记, same as: www.hmanwang.com, www.quanman8.com, www.lmmh.cc, www.xinmanba.com
            name = "Dida Manhua",
            baseUrl = "https://www.didamanhua.com",
            lang = "zh",
            isNsfw = true,
            className = "DidaManhua",
            sourceName = "嘀嗒漫画",
            overrideVersionCode = 0,
        ),
        SingleLang( // 103=脱身之法, same as: www.quanmanba.com, www.999mh.net
            name = "Dimanba",
            baseUrl = "https://www.dimanba.com",
            lang = "zh",
            isNsfw = true,
            className = "Dimanba",
            sourceName = "滴漫吧",
            overrideVersionCode = 0,
        ),
    )

    override fun createAll() {
        val userDir = System.getProperty("user.dir")!!
        sources.forEach {
            val themeClass = if (it.isNsfw) "MCCMSNsfw" else themeClass
            ThemeSourceGenerator.createGradleProject(it, themePkg, themeClass, baseVersionCode, userDir)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MCCMSGenerator().createAll()
        }
    }
}
