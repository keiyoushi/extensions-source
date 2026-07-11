plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenVN"
    versionCode = 17
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyenvn.sbs")
        }
    }
}
