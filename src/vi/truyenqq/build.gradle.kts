plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenQQ"
    className = "TruyenQQ"
    versionCode = 24
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://truyenqqko.com") {
            withCustom = true
        }
    }
}
