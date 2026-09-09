import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XXX Yaoi"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "pt-BR"
        baseUrl = "https://3xyaoi.com"
    }
}
