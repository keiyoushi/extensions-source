plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Real Life Comics"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://reallifecomics.com"
    }
}
