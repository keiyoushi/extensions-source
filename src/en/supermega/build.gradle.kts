import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SUPER MEGA"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.supermegacomics.com"
    }
}
