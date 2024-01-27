package eu.kanade.tachiyomi.multisrc.foolslide

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FoolSlideGenerator : ThemeSourceGenerator {

    override val themePkg = "foolslide"

    override val themeClass = "FoolSlide"

    override val baseVersionCode: Int = 3

    override val sources = listOf(
        MultiLang("FoolSlide Customizable", "", listOf("other")),
        MultiLang("HNI-Scantrad", "https://hni-scantrad.com", listOf("fr", "en"), className = "HNIScantradFactory", pkgName = "hniscantrad", overrideVersionCode = 1),
        SingleLang("Anata no Motokare", "https://motokare.xyz", "en", className = "AnataNoMotokare"),
        SingleLang("Death Toll Scans", "https://reader.deathtollscans.net", "en"),
        SingleLang("Evil Flowers", "https://reader.evilflowers.com", "en", overrideVersionCode = 1),
        SingleLang("Mangatellers", "https://reader.mangatellers.gr", "en"),
        SingleLang("Menudo-Fansub", "https://www.menudo-fansub.com", "es", className = "MenudoFansub", overrideVersionCode = 1),
        SingleLang("NIFTeam", "http://read-nifteam.info", "it"),
        SingleLang("Rama", "https://www.ramareader.it", "it"),
        SingleLang("Silent Sky", "https://reader.silentsky-scans.net", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FoolSlideGenerator().createAll()
        }
    }
}
