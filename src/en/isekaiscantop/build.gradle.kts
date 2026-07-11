plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "IsekaiScan.top (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://isekaiscan.top"
    }
}
