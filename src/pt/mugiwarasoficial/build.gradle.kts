import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mugiwaras Oficial"
    versionCode = 55
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "aurora"

    source {
        lang = "pt-BR"
        baseUrl = "https://mugiwarasoficial.org"
        versionId = 2
    }
}
