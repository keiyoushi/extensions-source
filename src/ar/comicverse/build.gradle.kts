plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Verse"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://arcomixverse.blogspot.com"
    }
}
