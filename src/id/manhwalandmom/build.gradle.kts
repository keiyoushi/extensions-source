plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaLand.mom"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://02.manhwaland.land"
    }
}
