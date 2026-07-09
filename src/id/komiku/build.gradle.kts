plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiku"
    versionCode = 21
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://komiku.org"
    }
}
