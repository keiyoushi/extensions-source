import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToonTop"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    theme = "mangak"
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://toontop.io"
        id = 1581110056159285576L
    }
}
