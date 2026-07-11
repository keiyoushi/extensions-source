plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ChoChoX"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "vercomics"

    source {
        lang = "es"
        baseUrl = "https://chochox.com"
    }
}
