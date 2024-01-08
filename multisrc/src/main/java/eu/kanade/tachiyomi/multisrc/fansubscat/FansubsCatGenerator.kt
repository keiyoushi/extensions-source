package eu.kanade.tachiyomi.multisrc.fansubscat

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FansubsCatGenerator : ThemeSourceGenerator {

    override val themePkg = "fansubscat"

    override val themeClass = "FansubsCat"

    override val baseVersionCode = 4

    override val sources = listOf(
        SingleLang(
            name = "Fansubs.cat",
            baseUrl = "https://manga.fansubs.cat",
            lang = "ca",
            className = "FansubsCatMain",
            isNsfw = false,
            pkgName = "fansubscat",
        ),
        SingleLang(
            name = "Fansubs.cat - Hentai",
            baseUrl = "https://hentai.fansubs.cat/manga",
            lang = "ca",
            className = "FansubsCatHentai",
            isNsfw = true,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = FansubsCatGenerator().createAll()
    }
}
