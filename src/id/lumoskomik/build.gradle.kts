import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LumosKomik"
    versionCode = 56
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "astromangareader"

    source {
        lang = "id"
        baseUrl = "https://03.lumosgg.com"
    }
}
