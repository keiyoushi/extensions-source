plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Guya"
    versionCode = 18
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "guya"

    source {
        lang = "en"
        baseUrl = "https://guya.cubari.moe"
    }
}
