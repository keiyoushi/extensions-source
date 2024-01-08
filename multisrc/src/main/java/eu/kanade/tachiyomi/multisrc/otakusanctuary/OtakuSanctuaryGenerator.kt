package eu.kanade.tachiyomi.multisrc.otakusanctuary

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceGenerator

class OtakuSanctuaryGenerator : ThemeSourceGenerator {

    override val themePkg = "otakusanctuary"

    override val themeClass = "OtakuSanctuary"

    override val baseVersionCode: Int = 4

    override val sources = listOf(
        MultiLang(
            "Otaku Sanctuary",
            "https://otakusan.net",
            listOf("all", "vi", "en", "it", "fr", "es"),
            isNsfw = true,
        ),
        MultiLang(
            "MyRockManga",
            "https://myrockmanga.com",
            listOf("all", "vi", "en", "it", "fr", "es"),
            isNsfw = true,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            OtakuSanctuaryGenerator().createAll()
        }
    }
}
