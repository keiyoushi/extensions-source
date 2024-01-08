package eu.kanade.tachiyomi.multisrc.a3manga

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class A3MangaGenerator : ThemeSourceGenerator {

    override val themePkg = "a3manga"

    override val themeClass = "A3Manga"

    override val baseVersionCode: Int = 3

    override val sources = listOf(
        SingleLang("A3 Manga", "https://www.a3manga.xyz", "vi"),
        SingleLang("Team Lanh Lung", "https://teamlanhlung.me", "vi", sourceName = "Team Lạnh Lùng", overrideVersionCode = 1),
        SingleLang("Ngon Phong", "https://www.ngonphong.com", "vi", sourceName = "Ngôn Phong", overrideVersionCode = 1),
        SingleLang("O Cu Meo", "https://www.ocumoe.com", "vi", sourceName = "Ổ Cú Mèo", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            A3MangaGenerator().createAll()
        }
    }
}
