plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenVN"
    versionCode = 17
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyenvn.sbs")
        }
    }
}
