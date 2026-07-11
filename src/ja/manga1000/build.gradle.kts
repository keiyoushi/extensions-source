plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga1000"
    versionCode = 13
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://hachiraw.win"
        versionId = 2
    }
}
