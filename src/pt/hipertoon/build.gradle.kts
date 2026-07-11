import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hipertoon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "hiper"

    source {
        lang = "pt-BR"
        baseUrl = "https://hipertoon.com"
    }
}
