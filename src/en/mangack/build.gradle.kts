plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangack"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangack.com"
    }
}
