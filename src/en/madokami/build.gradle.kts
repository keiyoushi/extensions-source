plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Madokami"
    versionCode = 14
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manga.madokami.al"
    }
}
