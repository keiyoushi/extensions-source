plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh"
    className = "GocTruyenTranh"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://goctruyentranh.com") {
            withCustom.set(true)
        }
    }
}
