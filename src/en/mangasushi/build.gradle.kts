plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangasushi"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangasushi.org"
    }
}
