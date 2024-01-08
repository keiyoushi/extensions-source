package eu.kanade.tachiyomi.multisrc.mangareader

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaReaderGenerator : ThemeSourceGenerator {
    override val themeClass = "MangaReader"
    override val themePkg = "mangareader"
    override val baseVersionCode = 1
    override val sources = listOf(
        MultiLang(
            name = "MangaReader",
            baseUrl = "https://mangareader.to",
            langs = listOf("en", "fr", "ja", "ko", "zh"),
            isNsfw = true,
            pkgName = "mangareaderto",
            overrideVersionCode = 3,
        ),
        MultiLang(
            name = "MangaFire",
            baseUrl = "https://mangafire.to",
            langs = listOf("en", "es", "es-419", "fr", "ja", "pt", "pt-BR"),
            isNsfw = true,
            overrideVersionCode = 3,
        ),
        SingleLang(
            name = "Manhuagold",
            baseUrl = "https://manhuagold.com",
            lang = "en",
            isNsfw = true,
            className = "Manhuagold",
            pkgName = "comickiba",
            overrideVersionCode = 33,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaReaderGenerator().createAll()
        }
    }
}
