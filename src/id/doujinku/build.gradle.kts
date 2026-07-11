plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujinku"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl {
            custom("https://doujinku.org")
        }
    }
}
