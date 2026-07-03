plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GantzVN"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl("https://gantzvn.com") {
            withCustom = true
        }
    }
}
