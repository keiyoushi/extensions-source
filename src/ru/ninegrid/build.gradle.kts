plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NineGrid"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        baseUrl("https://9grid.cc") {
            withCustom = true
        }
        lang = "ru"
    }
}
