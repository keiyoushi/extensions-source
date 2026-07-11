import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GlobalComix"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf(
        "sq", "ar", "bg", "bn", "pt-BR", "zh-Hans", "cs", "de", "da", "el",
        "en", "es", "fa", "fi", "fil", "fr", "hi", "hu", "id", "it",
        "he", "ja", "ko", "lv", "ms", "nl", "no", "pl", "pt", "ro",
        "ru", "sv", "sk", "sl", "ta", "th", "tr", "uk", "ur", "vi",
        "zh-Hant",
    ).forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://globalcomix.com"
            when (langCode) {
                "sq" -> id = 3356591153022230079L
                "da" -> id = 5048347663546425663L
            }
        }
    }

    deeplink {
        host("globalcomix.com")
        path("/c/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
