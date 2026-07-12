import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Colorcito Scan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "spicytheme"

    source {
        lang = "es"
        baseUrl = "https://colorcitoscan.com"
    }
}
