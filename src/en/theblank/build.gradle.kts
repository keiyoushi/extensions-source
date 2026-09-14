import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Blank"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "pam"

    source {
        lang = "en"
        baseUrl = "https://theblank.net"
        versionId = 2
    }
}
