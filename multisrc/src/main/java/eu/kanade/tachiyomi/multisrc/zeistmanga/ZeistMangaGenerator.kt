package eu.kanade.tachiyomi.multisrc.zeistmanga

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ZeistMangaGenerator : ThemeSourceGenerator {

    override val themePkg = "zeistmanga"

    override val themeClass = "ZeistManga"

    override val baseVersionCode: Int = 8

    override val sources = listOf(
        SingleLang("AnimeXNovel", "https://www.animexnovel.com", "pt-BR"),
        SingleLang("Asupan Komik", "https://www.asupankomik.my.id", "id", overrideVersionCode = 1),
        SingleLang("Eleven Scanlator", "https://elevenscanlator.blogspot.com", "pt-BR"),
        SingleLang("Guilda Tier Draw", "https://www.guildatierdraw.com", "pt-BR", isNsfw = true),
        SingleLang("Hijala", "https://hijala.blogspot.com", "ar"),
        SingleLang("KLManhua", "https://klmanhua.blogspot.com", "id", isNsfw = true),
        SingleLang("KomikRealm", "https://www.komikrealm.my.id", "id"),
        SingleLang("Ler 999", "https://ler999.blogspot.com", "pt-BR"),
        SingleLang("Loner Translations", "https://loner-tl.blogspot.com", "ar"),
        SingleLang("Manga Ai Land", "https://manga-ai-land.blogspot.com", "ar"),
        SingleLang("Manga Soul", "https://www.manga-soul.com", "ar", isNsfw = true),
        SingleLang("MikoRoku", "https://www.mikoroku.web.id", "id", isNsfw = true, overrideVersionCode = 1),
        SingleLang("Mikrokosmos Fansub", "https://mikrokosmosfb.blogspot.com", "tr", isNsfw = true),
        SingleLang("Shadow Çeviri", "https://shadowceviri.blogspot.com", "tr", className = "ShadowCeviri"),
        SingleLang("ShiyuraSub", "https://shiyurasub.blogspot.com", "id"),
        SingleLang("SobatManKu", "https://www.sobatmanku19.site", "id"),
        SingleLang("Tooncubus", "https://www.tooncubus.top", "id", isNsfw = true),
        SingleLang("Tyrant Scans", "https://www.tyrantscans.com", "pt-BR"),
        SingleLang("Yokai", "https://yokai-team.blogspot.com", "ar", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ZeistMangaGenerator().createAll()
        }
    }
}
