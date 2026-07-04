plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goda"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "goda"

    source {
        lang = "en"
        baseUrl = "https://manhuascans.org"
    }
}
