plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaTek"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://mangatek.com"
    }
}
