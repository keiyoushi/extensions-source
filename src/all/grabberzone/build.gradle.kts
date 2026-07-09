plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Grabber Zone"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "all"
        baseUrl = "https://grabber.zone"
    }
}
