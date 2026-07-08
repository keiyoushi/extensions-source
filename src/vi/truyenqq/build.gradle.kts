plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenQQ"
    versionCode = 24
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyenqqko.com")
        }
    }
}
