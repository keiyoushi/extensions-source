plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mikrokosmos Fansub"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "tr"
        baseUrl = "https://mikrokosmosfb.blogspot.com"
    }
}
