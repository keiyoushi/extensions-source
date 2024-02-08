package eu.kanade.tachiyomi.multisrc.manga18

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class Manga18Generator : ThemeSourceGenerator {

    override val themePkg = "manga18"

    override val themeClass = "Manga18"

    override val baseVersionCode = 1

    override val sources = listOf(
        SingleLang("18 Porn Comic", "https://18porncomic.com", "en", isNsfw = true, className = "EighteenPornComic"),
        SingleLang("Comic1000", "https://comic1000.com", "en", isNsfw = true),
        SingleLang("HANMAN18", "https://hanman18.com", "zh", isNsfw = true),
        SingleLang("Hentai3z.CC", "https://hentai3z.cc", "en", isNsfw = true, className = "Hentai3zCC"),
        SingleLang("Manga18.Club", "https://manga18.club", "en", isNsfw = true, className = "Manga18Club"),
        SingleLang("TuManhwas.Club", "https://tumanhwas.club", "es", isNsfw = true, className = "TuManhwasClub"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Manga18Generator().createAll()
        }
    }
}
