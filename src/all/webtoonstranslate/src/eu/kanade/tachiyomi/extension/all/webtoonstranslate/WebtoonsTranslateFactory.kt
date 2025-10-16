package eu.kanade.tachiyomi.extension.all.webtoonstranslate

import eu.kanade.tachiyomi.source.SourceFactory

class WebtoonsTranslateFactory : SourceFactory {
    override fun createSources() = listOf(
        WebtoonsTranslate("en", "ENG"),
        WebtoonsTranslate("zh-Hans", "CMN", 5196522547754842244),
        WebtoonsTranslate("zh-Hant", "CMT", 1016181401146312893),
        WebtoonsTranslate("th", "THA"),
        WebtoonsTranslate("id", "IND"),
        WebtoonsTranslate("fr", "FRA"),
        WebtoonsTranslate("vi", "VIE"),
        WebtoonsTranslate("ru", "RUS"),
        WebtoonsTranslate("ar", "ARA"),
        WebtoonsTranslate("fil", "FIL"),
        WebtoonsTranslate("de", "DEU"),
        WebtoonsTranslate("hi", "HIN"),
        WebtoonsTranslate("it", "ITA"),
        WebtoonsTranslate("ja", "JPN"),
        WebtoonsTranslate("pt-BR", "POR", 275670196689829558),
        WebtoonsTranslate("tr", "TUR"),
        WebtoonsTranslate("ms", "MAY"),
        WebtoonsTranslate("pl", "POL"),
        WebtoonsTranslate("pt", "POT", 9219933036054791613),
        WebtoonsTranslate("bg", "BUL"),
        WebtoonsTranslate("da", "DAN"),
        WebtoonsTranslate("nl", "NLD"),
        WebtoonsTranslate("ro", "RON"),
        WebtoonsTranslate("mn", "MON"),
        WebtoonsTranslate("el", "GRE"),
        WebtoonsTranslate("lt", "LIT"),
        WebtoonsTranslate("cs", "CES"),
        WebtoonsTranslate("sv", "SWE"),
        WebtoonsTranslate("bn", "BEN"),
        WebtoonsTranslate("fa", "PER"),
        WebtoonsTranslate("uk", "UKR"),
        WebtoonsTranslate("es", "SPA"),
    )
}
