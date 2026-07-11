plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dokiraw"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "liliana"

    source {
        lang = "ja"
        baseUrl = "https://dokiraw.win"
    }
}
