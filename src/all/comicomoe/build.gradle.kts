import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comico.moe"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "en", "es", "es-419", "fa", "fi", "fil", "fr", "he", "hi", "hu", "id",
        "it", "ja", "ko", "lt", "ms", "my", "nl", "no", "pl", "pt", "pt-BR", "ro", "ru", "sr", "sv", "th", "tr", "uk", "vi",
        "zh-Hans", "zh-Hant",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://comico.moe"
        }
    }

    deeplink {
        path("/manga/..*")
        path("/reader/..*")
        path("/author/..*")
    }
}
