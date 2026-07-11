plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Femjoy Hunter"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "masonry"

    source {
        lang = "all"
        baseUrl = "https://www.femjoyhunter.com"
    }
}
