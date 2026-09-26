import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mango Toons"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangotheme"

    source {
        lang = "pt-BR"
        baseUrl = "https://mangotoons.com"
    }
}
