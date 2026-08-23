import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawDEX"
    versionCode = 56
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl = "https://rawdex.net"
    }
}
