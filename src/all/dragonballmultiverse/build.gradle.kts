import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dragon Ball Multiverse"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    val dbmUrl = "https://www.dragonball-multiverse.com"

    listOf(
        "en", "fr", "ja", "zh", "es", "it", "pt", "de", "pl", "nl",
        "tr", "pt-BR", "hu", "ga", "ca", "no", "ru", "ro", "eu", "lt",
        "hr", "ko", "fi", "he", "bg", "sv", "el", "es-419", "ar", "fil",
        "la", "da", "co", "br", "vec", "lmo",
    ).forEach {
        source {
            lang = it
            baseUrl = dbmUrl
        }
    }
    source {
        name = "Dragon Ball Multiverse Parody"
        lang = "fr"
        baseUrl = dbmUrl
    }
}
