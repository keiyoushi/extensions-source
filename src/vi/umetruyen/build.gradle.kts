import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "UmeTruyen"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl = "https://umetruyenz.org"
    }
}
