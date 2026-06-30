plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Holy Scans"
    versionCode = 51
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://holyscans.com.tr"
    }
}
