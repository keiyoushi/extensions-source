package eu.kanade.tachiyomi.multisrc.webtoons

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceGenerator

class WebtoonsTranslateGenerator : ThemeSourceGenerator {
    override val themePkg = "webtoons"

    override val themeClass = "WebtoonsTranslation"

    override val baseVersionCode: Int = 2

    override val sources = listOf(
        MultiLang("Webtoons.com Translations", "https://translate.webtoons.com", listOf("en", "zh-Hans", "zh-Hant", "th", "id", "fr", "vi", "ru", "ar", "fil", "de", "hi", "it", "ja", "pt-BR", "tr", "ms", "pl", "pt", "bg", "da", "nl", "ro", "mn", "el", "lt", "cs", "sv", "bn", "fa", "uk", "es"), className = "WebtoonsTranslateFactory", pkgName = "webtoonstranslate", overrideVersionCode = 4),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WebtoonsTranslateGenerator().createAll()
        }
    }
}
