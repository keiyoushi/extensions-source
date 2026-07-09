plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gone with the Blastwave"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.blastwave-comic.com"
    }
}
