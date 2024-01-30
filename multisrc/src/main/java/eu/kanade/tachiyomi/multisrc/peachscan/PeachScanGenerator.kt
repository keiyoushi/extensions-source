package eu.kanade.tachiyomi.multisrc.peachscan

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PeachScanGenerator : ThemeSourceGenerator {

    override val themePkg = "peachscan"

    override val themeClass = "PeachScan"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("Aurora Scan", "https://aurorascan.net", "pt-BR"),
        SingleLang("Dango Scan", "https://dangoscan.com.br", "pt-BR"),
        SingleLang("Mode Scanlator", "https://modescanlator.com", "pt-BR"),
        SingleLang("Nazarick Scan", "https://nazarickscan.com.br", "pt-BR"),
        SingleLang("RF Dragon Scan", "https://rfdragonscan.com", "pt-BR"),
        SingleLang("Wicked Witch Scan", "https://wicked-witch-scan.com", "pt-BR", pkgName = "wickedwitchscannovo", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PeachScanGenerator().createAll()
        }
    }
}
