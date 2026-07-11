plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mikrokosmos Fansub"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "tr"
        baseUrl = "https://mikrokosmosfb.blogspot.com"
    }
}
