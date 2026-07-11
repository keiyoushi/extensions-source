import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yupmanga"
    versionCode = 16
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://www.yupmanga.com"
    }
}
