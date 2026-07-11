import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "e621"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://e621.net"
    }
}
