plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Duck Webcomics"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.theduckwebcomics.com"
    }
}
