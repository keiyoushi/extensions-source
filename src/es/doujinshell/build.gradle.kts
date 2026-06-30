plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinsHell"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://doujinshell.net"
    }
}
