plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hachirumi"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "guya"

    source {
        lang = "en"
        baseUrl = "https://hachirumi.com"
    }
}
