import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaWeb"
    versionCode = 13
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwaweb.com"
    }
}
