package eu.kanade.tachiyomi.multisrc.bilibili

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class BilibiliGenerator : ThemeSourceGenerator {

    override val themePkg = "bilibili"

    override val themeClass = "Bilibili"

    override val baseVersionCode: Int = 9

    override val sources = listOf(
        MultiLang(
            name = "BILIBILI COMICS",
            baseUrl = "https://www.bilibilicomics.com",
            langs = listOf("en", "zh-Hans", "id", "es", "fr"),
            className = "BilibiliComicsFactory",
            overrideVersionCode = 3,
        ),
        SingleLang(
            name = "BILIBILI MANGA",
            baseUrl = "https://manga.bilibili.com",
            lang = "zh-Hans",
            className = "BilibiliManga",
            sourceName = "哔哩哔哩漫画",
            overrideVersionCode = 2,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BilibiliGenerator().createAll()
        }
    }
}
