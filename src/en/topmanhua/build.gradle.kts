plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Manhua"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangatop.org"
    }
}
