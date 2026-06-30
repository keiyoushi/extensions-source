plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaClub.net"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "ko").forEach {
        source {
            lang = it
            baseUrl = "https://manhwaclub.net"
        }
    }
}
