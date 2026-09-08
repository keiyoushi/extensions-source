import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadotnet"
    versionCode = 19
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "en.mangadotnet"

    listOf(
        "ar", "bn", "bg", "my", "zh", "zh-Hant", "cs", "da", "nl", "en", "tl",
        "fi", "fr", "ka", "de", "el", "he", "hi", "hu", "id", "it", "ja", "ko", "la", "lt",
        "ms", "mn", "no", "fa", "pl", "pt", "pt-BR", "ro", "ru", "es", "es-419", "sv", "th",
        "tr", "uk", "vi",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangadot.net"
        }
    }

    deeplink {
        host("mangadot.net")
        path("/manga/..*")
        path("/chapter/..*")
        path("/volume/..*")
    }
}
