plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Armageddon"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://www.silentquill.net"
    }
}
