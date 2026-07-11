plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeDamTruyen"
    versionCode = 7
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://saytongtaii.site"
    }
}
