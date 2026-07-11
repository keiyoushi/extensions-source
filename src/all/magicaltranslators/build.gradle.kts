import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Magical Translators"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "guya"

    listOf("en", "es", "pl").forEach { language ->
        source {
            lang = language
            baseUrl = "https://mahoushoujobu.com"
        }
    }
}
