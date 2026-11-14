import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaOnline"
    versionCode = 61
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://manhwa-online.com"
    }
}
