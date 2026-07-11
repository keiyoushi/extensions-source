plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Holy Scans"
    versionCode = 51
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://holyscans.com.tr"
    }
}
