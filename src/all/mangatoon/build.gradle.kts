import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaToon (Limited)"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("zh", "en", "id", "vi", "es", "th", "fr", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://mangatoon.mobi"
            if (it == "pt-BR") id = 2064722193112934135
        }
    }
}
