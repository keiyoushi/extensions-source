package eu.kanade.tachiyomi.multisrc.peachscan

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PeachScanGenerator : ThemeSourceGenerator {

    override val themePkg = "peachscan"

    override val themeClass = "PeachScan"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("Mode Scanlator", "https://modescanlator.com", "pt-BR"),
        SingleLang("Nazarick Scan", "https://nazarickscan.com.br", "pt-BR"),
        SingleLang("RF Dragon Scan", "https://rfdragonscan.com", "pt-BR"),
        SingleLang("Wicked Witch Scan (Novo)", "https://wicked-witch-scan.com", "pt-BR", className = "WickedWitchScan", pkgName = "wickedwitchscannovo", sourceName = "Wicked Witch Scan", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PeachScanGenerator().createAll()
        }
    }
}
