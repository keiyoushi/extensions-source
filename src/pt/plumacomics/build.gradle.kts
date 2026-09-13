import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pluma Comics"
    versionCode = 51
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://plumacomics.cloud"
        versionId = 6
    }
}
