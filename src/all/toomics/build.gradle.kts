import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toomics"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "zh-Hans", "zh-Hant", "es-419", "es", "it", "de", "fr", "pt-BR").forEach { langCode ->
        source {
            name = "Toomics (Only free chapters)"
            lang = langCode
            baseUrl = "https://global.toomics.com"
            when (langCode) {
                "zh-Hans" -> id = 2191753978421234924L
                "zh-Hant" -> id = 371640888113435809L
                "es-419" -> id = 7362369816539610504L
                "pt-BR" -> id = 4488498756724948818L
            }
        }
    }
}
