plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OreManga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://www.oremanga.net"
    }
}
