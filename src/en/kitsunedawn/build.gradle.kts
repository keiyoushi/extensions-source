import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kitsune Dawn"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "keyoapp"

    source {
        baseUrl = "https://kitsunedawn.com"
        lang = "en"
    }
}
