plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Softkomik"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://softkomik.co"
    }
}
