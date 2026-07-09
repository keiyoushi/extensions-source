plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa XXL"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentaitnt.net"
        versionId = 2
    }
}
