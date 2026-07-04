plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "vomic"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl("https://www.vomicmh.com") {
            withCustom = true
        }
    }
}
