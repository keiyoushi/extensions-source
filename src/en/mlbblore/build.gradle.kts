import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MLBB Lore"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "MLBB Lore Comics"
        lang = "en"
        baseUrl = "https://play.mobilelegends.com"
    }
}
