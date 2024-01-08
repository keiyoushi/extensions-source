package eu.kanade.tachiyomi.multisrc.mymangacms

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MyMangaCMSGenerator : ThemeSourceGenerator {

    override val themePkg = "mymangacms"

    override val themeClass = "MyMangaCMS"

    override val baseVersionCode: Int = 2

    override val sources = listOf(
        SingleLang(
            "TruyenTranhLH",
            "https://truyenlh.com",
            "vi",
            isNsfw = true,
            overrideVersionCode = 10,
        ),
        SingleLang(
            "Manhwa18",
            "https://manhwa18.com",
            "en",
            isNsfw = true,
            overrideVersionCode = 9,
        ),
        MultiLang(
            "Manhwa18.net",
            "https://manhwa18.net",
            listOf("en"),
            className = "Manhwa18Net",
            isNsfw = true,
            overrideVersionCode = 8,
        ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MyMangaCMSGenerator().createAll()
        }
    }
}
