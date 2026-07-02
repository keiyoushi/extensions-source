plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentara"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentara.com"
    }
}
