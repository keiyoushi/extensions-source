plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tranh18"
    className = "Tranh18"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://tranh18.cc") {
            withCustom.set(true)
        }
    }
}
