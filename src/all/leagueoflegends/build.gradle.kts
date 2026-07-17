import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "League of Legends"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    val locales = mapOf(
        "en" to "en_us", "de" to "de_de", "es" to "es_es", "fr" to "fr_fr",
        "it" to "it_it", "pl" to "pl_pl", "el" to "el_gr", "ro" to "ro_ro",
        "hu" to "hu_hu", "cs" to "cs_cz", "es-419" to "es_mx", "pt-BR" to "pt_br",
        "ja" to "ja_jp", "ru" to "ru_ru", "tr" to "tr_tr", "ko" to "ko_kr",
    )
    locales.forEach { (langCode, locale) ->
        source {
            lang = langCode
            baseUrl = "https://universe.leagueoflegends.com/$locale/comic/"
        }
    }
}
