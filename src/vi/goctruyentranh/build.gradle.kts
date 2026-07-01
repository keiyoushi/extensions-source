plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "GocTruyenTranh"
        lang = "vi"
        baseUrl("https://goctruyentranh.com") {
            withCustom = true
        }
    }
}
