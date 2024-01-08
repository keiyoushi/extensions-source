package eu.kanade.tachiyomi.multisrc.comicgamma

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ComicGammaGenerator : ThemeSourceGenerator {
    override val themeClass = "ComicGamma"
    override val themePkg = "comicgamma"
    override val baseVersionCode = 6
    override val sources = listOf(
        SingleLang(
            name = "Web Comic Gamma",
            baseUrl = "https://webcomicgamma.takeshobo.co.jp",
            lang = "ja",
            isNsfw = false,
            className = "WebComicGamma",
            pkgName = "webcomicgamma",
            sourceName = "Web Comic Gamma",
            overrideVersionCode = 0,
        ),
        SingleLang(
            name = "Web Comic Gamma Plus",
            baseUrl = "https://gammaplus.takeshobo.co.jp",
            lang = "ja",
            isNsfw = true,
            className = "WebComicGammaPlus",
            pkgName = "webcomicgammaplus",
            sourceName = "Web Comic Gamma Plus",
            overrideVersionCode = 0,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ComicGammaGenerator().createAll()
        }
    }
}
