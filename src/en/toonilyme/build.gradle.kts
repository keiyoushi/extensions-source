import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonily.me"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    theme = "mangak"
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://toontop.io"
    }
}


