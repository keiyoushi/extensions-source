plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GirlsTop"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://en.girlstop.info"
    }
}
