package eu.kanade.tachiyomi.multisrc.hentaihand

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class HentaiHandGenerator : ThemeSourceGenerator {

    override val themePkg = "hentaihand"

    override val themeClass = "HentaiHand"

    override val baseVersionCode: Int = 2

    override val sources = listOf(
        MultiLang("HentaiHand", "https://hentaihand.com", listOf("all", "ja", "en", "zh", "bg", "ceb", "other", "tl", "ar", "el", "sr", "jv", "uk", "tr", "fi", "la", "mn", "eo", "sk", "cs", "ko", "ru", "it", "es", "pt-BR", "th", "fr", "id", "vi", "de", "pl", "hu", "nl", "hi"), isNsfw = true, overrideVersionCode = 5),
        MultiLang("nHentai.com (unoriginal)", "https://nhentai.com", listOf("all", "ja", "en", "zh", "bg", "ceb", "other", "tl", "ar", "el", "sr", "jv", "uk", "tr", "fi", "la", "mn", "eo", "sk", "cs", "ko", "ru", "it", "es", "pt-BR", "th", "fr", "id", "vi", "de", "pl", "hu", "nl", "hi"), isNsfw = true, className = "NHentaiComFactory", overrideVersionCode = 4),
        SingleLang("HentaiSphere", "https://hentaisphere.com", "en", isNsfw = true),
        SingleLang("ManhwaClub", "https://manhwa.club", "en", isNsfw = true, overrideVersionCode = 3),
        SingleLang("ReadManhwa", "https://readmanhwa.com", "en", isNsfw = true, overrideVersionCode = 10),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            HentaiHandGenerator().createAll()
        }
    }
}
