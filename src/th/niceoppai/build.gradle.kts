import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Niceoppai"
    versionCode = 29
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://www.niceoppai.net"
    }
}
