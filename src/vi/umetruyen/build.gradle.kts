plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "UmeTruyen"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl = "https://umetruyenz.org"
    }
}
