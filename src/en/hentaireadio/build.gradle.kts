plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiRead.io"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentairead.io"
    }
}
