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
        SingleLang("Baixar Hentai", "https://leitura.baixarhentai.net", "pt-BR", isNsfw = true, overrideVersionCode = 4),
        SingleLang("Death Toll Scans", "https://reader.deathtollscans.net", "en"),
        SingleLang("Evil Flowers", "https://reader.evilflowers.com", "en", overrideVersionCode = 1),
        SingleLang("Le Cercle du Scan", "https://lel.lecercleduscan.com", "fr", className = "LeCercleDuScan", overrideVersionCode = 1),
        SingleLang("Lilyreader", "https://manga.smuglo.li", "en"),
        SingleLang("MangaScouts", "http://onlinereader.mangascouts.org", "de", overrideVersionCode = 1),
        SingleLang("Mangatellers", "https://reader.mangatellers.gr", "en"),
        SingleLang("Menudo-Fansub", "https://www.menudo-fansub.com", "es", className = "MenudoFansub", overrideVersionCode = 1),
        SingleLang("NIFTeam", "http://read-nifteam.info", "it"),
        SingleLang("PowerManga", "https://reader.powermanga.org", "it", className = "PowerMangaIT"),
        SingleLang("Rama", "https://www.ramareader.it", "it"),
        SingleLang("Silent Sky", "https://reader.silentsky-scans.net", "en"),
        SingleLang("Wanted Team", "https://reader.onepiecenakama.pl", "pl"),
        SingleLang("Русификация", "https://rusmanga.ru", "ru", className = "Russification"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FoolSlideGenerator().createAll()
        }
    }
}
