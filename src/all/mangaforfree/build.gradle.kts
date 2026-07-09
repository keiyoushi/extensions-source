plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaForFree.net"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "ko", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangaforfree.net"
        }
    }
}
