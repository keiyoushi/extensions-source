plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CManga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://cmangax17.com") {
            withCustom = true
        }
    }
}
