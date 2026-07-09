plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenTuoiTho"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl = "https://truyentuoitho.online"
    }
}
