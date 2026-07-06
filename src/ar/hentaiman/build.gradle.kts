plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiMan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://hentaiman.net"
    }
}
