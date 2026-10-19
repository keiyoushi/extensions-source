import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa-raw"
    versionCode = 3
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "all"
        baseUrl = "https://manhwa-raw.com"
    }
}
