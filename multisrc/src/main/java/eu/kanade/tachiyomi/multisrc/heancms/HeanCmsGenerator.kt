package eu.kanade.tachiyomi.multisrc.heancms

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class HeanCmsGenerator : ThemeSourceGenerator {

    override val themePkg = "heancms"

    override val themeClass = "HeanCms"

    override val baseVersionCode: Int = 20

    override val sources = listOf(
        SingleLang("Omega Scans", "https://omegascans.org", "en", isNsfw = true, overrideVersionCode = 18),
        SingleLang("Perf Scan", "https://perf-scan.fr", "fr"),
        SingleLang("Temple Scan", "https://templescan.net", "en", isNsfw = true, overrideVersionCode = 16),
        SingleLang("YugenMangas", "https://yugenmangas.net", "es", isNsfw = true, overrideVersionCode = 9),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            HeanCmsGenerator().createAll()
        }
    }
}
