import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa Scan"
    versionCode = 56
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwascanx.lat"
        id = 7172992930543738693L
    }
}
