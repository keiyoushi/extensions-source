plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RuMIX"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl("https://rumix.me") {
            withCustom = true
        }
        lang = "ru"
    }
}
