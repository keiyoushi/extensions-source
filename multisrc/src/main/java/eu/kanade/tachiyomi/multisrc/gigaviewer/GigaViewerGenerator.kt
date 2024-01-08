package eu.kanade.tachiyomi.multisrc.gigaviewer

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class GigaViewerGenerator : ThemeSourceGenerator {

    override val themePkg = "gigaviewer"

    override val themeClass = "GigaViewer"

    override val baseVersionCode: Int = 5

    override val sources = listOf(
        SingleLang("Comic Days", "https://comic-days.com", "ja"),
        SingleLang("Comic Gardo", "https://comic-gardo.com", "ja"),
        SingleLang("Comiplex", "https://viewer.heros-web.com", "ja"),
        SingleLang("Corocoro Online", "https://corocoro.jp", "ja"),
        SingleLang("Kurage Bunch", "https://kuragebunch.com", "ja"),
        SingleLang("MAGCOMI", "https://magcomi.com", "ja", className = "MagComi"),
        SingleLang("Magazine Pocket", "https://pocket.shonenmagazine.com", "ja"),
        SingleLang("Shonen Jump+", "https://shonenjumpplus.com", "ja", pkgName = "shonenjumpplus", className = "ShonenJumpPlus", overrideVersionCode = 2),
        SingleLang("Sunday Web Every", "https://www.sunday-webry.com", "ja"),
        SingleLang("Tonari no Young Jump", "https://tonarinoyj.jp", "ja", className = "TonariNoYoungJump"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GigaViewerGenerator().createAll()
        }
    }
}
