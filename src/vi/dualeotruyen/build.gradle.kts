plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dua Leo Truyen"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Dưa Leo Truyện"
        lang = "vi"
        baseUrl("https://dualeotruyendc.com") {
            withCustom = true
        }
    }
}
