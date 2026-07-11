plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yona Bar"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://yonaber.com"
    }
}
