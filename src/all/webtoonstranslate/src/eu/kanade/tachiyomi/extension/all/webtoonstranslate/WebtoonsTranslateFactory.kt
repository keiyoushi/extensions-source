package eu.kanade.tachiyomi.extension.all.webtoonstranslate

import eu.kanade.tachiyomi.multisrc.webtoons.WebtoonsTranslate
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WebtoonsTranslateFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsTranslateEN(),
        WebtoonsTranslateZH_CMN(),
        WebtoonsTranslateZH_CMY(),
        WebtoonsTranslateTH(),
        WebtoonsTranslateID(),
        WebtoonsTranslateFR(),
        WebtoonsTranslateVI(),
        WebtoonsTranslateRU(),
        WebtoonsTranslateAR(),
        WebtoonsTranslateFIL(),
        WebtoonsTranslateDE(),
        WebtoonsTranslateHI(),
        WebtoonsTranslateIT(),
        WebtoonsTranslateJA(),
        WebtoonsTranslatePT_POR(),
        WebtoonsTranslateTR(),
        WebtoonsTranslateMS(),
        WebtoonsTranslatePL(),
        WebtoonsTranslatePT_POT(),
        WebtoonsTranslateBG(),
        WebtoonsTranslateDA(),
        WebtoonsTranslateNL(),
        WebtoonsTranslateRO(),
        WebtoonsTranslateMN(),
        WebtoonsTranslateEL(),
        WebtoonsTranslateLT(),
        WebtoonsTranslateCS(),
        WebtoonsTranslateSV(),
        WebtoonsTranslateBN(),
        WebtoonsTranslateFA(),
        WebtoonsTranslateUK(),
        WebtoonsTranslateES(),
    )
}
class WebtoonsTranslateEN : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "en", "ENG")
class WebtoonsTranslateZH_CMN : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "zh-Hans", "CMN") {
    override val id: Long = 5196522547754842244
}
class WebtoonsTranslateZH_CMY : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "zh-Hant", "CMT") {
    override val id: Long = 1016181401146312893
}
class WebtoonsTranslateTH : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "th", "THA")
class WebtoonsTranslateID : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "id", "IND")
class WebtoonsTranslateFR : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "fr", "FRA")
class WebtoonsTranslateVI : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "vi", "VIE")
class WebtoonsTranslateRU : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "ru", "RUS")
class WebtoonsTranslateAR : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "ar", "ARA")
class WebtoonsTranslateFIL : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "fil", "FIL")
class WebtoonsTranslateDE : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "de", "DEU")
class WebtoonsTranslateHI : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "hi", "HIN")
class WebtoonsTranslateIT : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "it", "ITA")
class WebtoonsTranslateJA : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "ja", "JPN")
class WebtoonsTranslatePT_POR : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "pt-BR", "POR") {
    // Hardcode the id because the language code was wrong.
    override val id: Long = 275670196689829558
}
class WebtoonsTranslateTR : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "tr", "TUR")
class WebtoonsTranslateMS : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "ms", "MAY")
class WebtoonsTranslatePL : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "pl", "POL")
class WebtoonsTranslatePT_POT : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "pt", "POT") {
    override val id: Long = 9219933036054791613
}
class WebtoonsTranslateBG : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "bg", "BUL")
class WebtoonsTranslateDA : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "da", "DAN")
class WebtoonsTranslateNL : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "nl", "NLD")
class WebtoonsTranslateRO : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "ro", "RON")
class WebtoonsTranslateMN : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "mn", "MON")
class WebtoonsTranslateEL : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "el", "GRE")
class WebtoonsTranslateLT : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "lt", "LIT")
class WebtoonsTranslateCS : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "cs", "CES")
class WebtoonsTranslateSV : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "sv", "SWE")
class WebtoonsTranslateBN : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "bn", "BEN")
class WebtoonsTranslateFA : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "fa", "PER")
class WebtoonsTranslateUK : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "uk", "UKR")
class WebtoonsTranslateES : WebtoonsTranslate("Webtoons.com Translations", "https://translate.webtoons.com", "es", "SPA")
