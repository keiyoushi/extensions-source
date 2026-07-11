import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sunday Web Every"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://www.sunday-webry.com"
    }
}
